/**
 * 주문 단위 이벤트 원장.
 *
 * 전역 카운터만으로는 "어느 드리미에게 매칭이 갔는지", "와야 할 알람이 안 왔는지"를 판정할 수 없다.
 * 여기서는 주문 1건 = 레코드 1개로 기록하고, 각 단계마다 뒤따라야 할 이벤트가 제한 시간 안에
 * 도착했는지를 대조한다.
 *
 * 판정 규칙(선행 사실 → 기대 이벤트):
 *  1. 주문 생성 성공        → 드리미 누군가에게 offer_popup          (T_OFFER)
 *  2. 오퍼 수락 제출        → 그 주문 부르미에게 dreami_info          (T_INFO)
 *  3. 승자 외 형제 오퍼     → 각 드리미에게 offer_closed              (T_CLOSED)
 *  4. 드리미 확정 200       → delivery_started_dreami / _boormi 양쪽  (T_DELIVERY)
 *  5. 응답 없는 오퍼        → 30초 TTL 만료 후 offer_closed           (T_EXPIRE)
 *
 * 판정 시점에 제한 시간이 아직 지나지 않은 건은 유실이 아니라 "판정보류"로 따로 센다.
 *
 * ── 승자를 accept 응답으로 판정하지 않는 이유 ──
 * `POST /dreami/offers/{id}/accept`은 `matchingEngine.submit(new AcceptByDreami(...))`으로
 * 큐에 넣고 곧바로 200을 준다. 선착순 승패는 그 뒤 엔진이 결정하고 SSE로만 알린다 —
 * 승자에게는 부르미 쪽 `dreami_info`가, 패자에게는 `offer_error("이미 다른 드리미가
 * 수락한 주문입니다.")`가 간다. 그래서 200은 "제출 성공"일 뿐이고, 한 주문의 오퍼 전부가
 * 200을 받는 일이 정상적으로 일어난다. 승자는 `dreami_info`의 offerId로만 판정한다.
 */

export const TIMEOUTS = {
  offer: Number(process.env.T_OFFER ?? 15_000),
  info: Number(process.env.T_INFO ?? 10_000),
  closed: Number(process.env.T_CLOSED ?? 10_000),
  delivery: Number(process.env.T_DELIVERY ?? 10_000),
  expire: Number(process.env.T_EXPIRE ?? 45_000),
};

export function percentile(values, p) {
  if (values.length === 0) return null;
  const sorted = [...values].sort((a, b) => a - b);
  return sorted[Math.min(sorted.length - 1, Math.floor((sorted.length * p) / 100))];
}

function stats(values) {
  return {
    count: values.length,
    p50: percentile(values, 50),
    p95: percentile(values, 95),
    p99: percentile(values, 99),
  };
}

/** 표본 배열들의 묶음을 같은 키의 통계 묶음으로 바꾼다. */
export function statsOf(groups) {
  return Object.fromEntries(Object.entries(groups).map(([key, values]) => [key, stats(values)]));
}

export function createLedger() {
  /** orderId → 주문 레코드 */
  const orders = new Map();
  /** offerId → 오퍼 레코드(주문 레코드 안의 것과 같은 객체) */
  const offers = new Map();

  const counters = {
    createAttempt: 0,
    createOk: 0,
    createFailKakao: 0,
    createFailLimit: 0,
    createFailOther: 0,
    /** accept 200. 큐 제출 성공일 뿐 수락 성사가 아니다 — 위 주석 참고. */
    acceptSubmitted: 0,
    acceptFail: 0,
    confirmOk: 0,
    confirmFail: 0,
    boormiRejected: 0,
    offerError: 0,
    /** 원장이 모르는 오퍼/주문에 붙은 이벤트. 이전 런의 잔여 상태를 의심할 근거가 된다. */
    orphanEvents: 0,
  };
  /** 엔드포인트별 API 왕복 시간. 톰캣 큐 + 트랜잭션 + DB까지의 동기 서버 처리 시간이다. */
  const rtt = { create: [], accept: [], confirm: [] };
  /** 드리미 이메일 → offer_error 수신 시각들. 형제 오퍼 마감을 이걸로 알게 된 경우를 가린다. */
  const offerErrorsByDreami = new Map();
  const closedReasons = new Map();
  const createErrors = new Map();
  const acceptErrors = new Map();
  const confirmErrors = new Map();

  function bump(map, key) {
    map.set(key, (map.get(key) ?? 0) + 1);
  }

  function order(orderId) {
    return orders.get(orderId);
  }

  /**
   * 주문 레코드를 가져오거나 만든다.
   *
   * SSE 이벤트가 주문 생성 HTTP 응답보다 먼저 도착하는 일이 흔하다 — 엔진이 같은 트랜잭션
   * 커밋 직후에 오퍼를 뿌리기 때문에, 응답이 네트워크를 타고 돌아오기 전에 offer_popup이 온다.
   * 응답을 기다렸다 레코드를 만들면 그 이벤트들이 전부 원장 밖으로 새므로, 이벤트 쪽에서도
   * 레코드를 열어 두고 나중에 createOk가 생성 정보를 채운다.
   */
  function ensureOrder(orderId) {
    let rec = orders.get(orderId);
    if (!rec) {
      rec = {
        orderId,
        boormiEmail: null,
        // createReqAt은 요청을 보낸 시각, createdAt은 응답을 받은 시각이다. 서버 구간은 요청 발신부터 재야
        // API 왕복이 빠지지 않고, 응답보다 먼저 도착하는 SSE 때문에 음수가 되지도 않는다.
        createReqAt: null,
        createdAt: null,
        offers: [],
        dreamiInfoAt: null,
        winnerOfferId: null,
        confirmReqAt: null,
        confirmAt: null,
        confirmOk: false,
        winnerDreamiEmail: null,
        deliveryDreamiAt: null,
        deliveryBoormiAt: null,
      };
      orders.set(orderId, rec);
    }
    return rec;
  }

  return {
    orders,
    counters,
    closedReasons,

    createAttempt() {
      counters.createAttempt++;
    },

    createOk(orderId, boormiEmail, reqAt, at) {
      counters.createOk++;
      rtt.create.push(at - reqAt);
      const rec = ensureOrder(orderId);
      rec.boormiEmail = boormiEmail;
      rec.createReqAt = reqAt;
      rec.createdAt = at;
    },

    createFail(message) {
      // 카카오 지오코딩/경로 실패와 동시 주문 제한은 매칭 성능이 아니라 사전 조건의 문제다.
      if (/GEO|ADDRESS|KAKAO|DIRECTION|경로|주소/i.test(message)) counters.createFailKakao++;
      else if (/TOO_MANY_ACTIVE_ORDERS/i.test(message)) counters.createFailLimit++;
      else counters.createFailOther++;
      bump(createErrors, message.slice(0, 160));
    },

    offerPopup({ orderId, offerId, dreamiEmail, at }) {
      const rec = ensureOrder(orderId);
      const offer = {
        offerId,
        dreamiEmail,
        popupAt: at,
        acceptReqAt: null,
        acceptAt: null,
        acceptOk: null,
        closedAt: null,
        closedReason: null,
      };
      rec.offers.push(offer);
      offers.set(offerId, offer);
    },

    acceptResult({ offerId, ok, reqAt, at, error }) {
      if (ok) counters.acceptSubmitted++;
      else {
        counters.acceptFail++;
        if (error) bump(acceptErrors, error.slice(0, 160));
      }
      rtt.accept.push(at - reqAt);
      const offer = offers.get(offerId);
      if (!offer) return;
      offer.acceptReqAt = reqAt;
      offer.acceptAt = at;
      offer.acceptOk = ok;
    },

    offerClosed({ offerId, reason, at }) {
      bump(closedReasons, reason ?? "사유없음");
      const offer = offers.get(offerId);
      if (!offer) {
        counters.orphanEvents++;
        return;
      }
      offer.closedAt = at;
      offer.closedReason = reason ?? null;
    },

    dreamiInfo({ orderId, offerId, at }) {
      const rec = ensureOrder(orderId);
      if (rec.dreamiInfoAt === null) rec.dreamiInfoAt = at;
      // 승자는 여기서만 정해진다. accept 200은 큐 제출일 뿐이다.
      rec.winnerDreamiEmail = offers.get(offerId)?.dreamiEmail ?? rec.winnerDreamiEmail;
      rec.winnerOfferId = offerId;
    },

    confirmResult({ orderId, ok, reqAt, at, error }) {
      if (ok) counters.confirmOk++;
      else {
        counters.confirmFail++;
        if (error) bump(confirmErrors, error.slice(0, 160));
      }
      rtt.confirm.push(at - reqAt);
      const rec = ensureOrder(orderId);
      rec.confirmReqAt = reqAt;
      rec.confirmAt = at;
      rec.confirmOk = ok;
    },

    deliveryStarted({ orderId, side, at }) {
      const rec = ensureOrder(orderId);
      if (side === "dreami") rec.deliveryDreamiAt ??= at;
      else rec.deliveryBoormiAt ??= at;
    },

    boormiRejected() {
      counters.boormiRejected++;
    },

    /**
     * 페이로드가 메시지뿐이라 어느 오퍼인지 알 수 없다. 드리미별 수신 시각만 남겨서,
     * 형제 오퍼 마감을 offer_closed 대신 이걸로 통지받은 경우를 유실과 구분한다.
     */
    offerError({ dreamiEmail, at }) {
      counters.offerError++;
      if (!dreamiEmail) return;
      const list = offerErrorsByDreami.get(dreamiEmail) ?? [];
      list.push(at);
      offerErrorsByDreami.set(dreamiEmail, list);
    },

    /**
     * 유실 판정과 집계. `finishedAt` 기준으로 제한 시간이 지난 건만 판정한다.
     * `dreamiCount`는 완주 상한 판별에 쓴다 — 매칭이 성사된 드리미는 다시 온라인이 될 수 없어서
     * 계정 수를 넘는 주문은 구조적으로 오퍼를 받지 못한다.
     */
    finalize({ finishedAt, dreamiCount, externalOrderIds = [] }) {
      const external = new Set(externalOrderIds);
      const missing = [];
      const pending = [];
      const capacityBlocked = [];
      /** 형제 오퍼가 offer_closed 대신 offer_error로 마감을 통지받은 건. 유실이 아니다. */
      const closedViaError = [];
      /** createOk가 끝내 안 붙은 주문 = 이 런의 부하가 만들지 않은 주문. 인메모리 잔여 상태의 흔적. */
      const orphanOrders = [];
      /** 그중 브라우저 부르미가 만든 주문. 정상이며, 잔여 상태 경고에서 빼야 한다. */
      const browserOrders = [];
      /** 부하 주문인데 원장이 모르는 드리미(=브라우저 드리미)가 이긴 건. 유실이 아니다. */
      const externalWinnerOrders = [];

      const add = (list, item) => list.push(item);

      const judge = (list, since, limit, item) => {
        if (finishedAt - since >= limit) add(list, item);
        else add(pending, { ...item, waitedMs: finishedAt - since, limitMs: limit });
      };

      /** 이 드리미가 `since` 이후에 offer_error를 받았는가. */
      const gotErrorAfter = (dreamiEmail, since) =>
        (offerErrorsByDreami.get(dreamiEmail) ?? []).some((t) => t >= since);

      /**
       * 확정(confirm 200)까지 간 주문. 드리미 계정을 소진시킨 건 확정 시점이므로 완주 상한 판별은 이 값으로 한다.
       */
      const matchedCount = [...orders.values()].filter((o) => o.confirmOk).length;

      /**
       * 완주 = 배달이 실제로 시작된 주문. confirm 200은 "매칭엔진에 제출됐다"까지만 뜻하고,
       * DELIVERY 생성은 엔진 스레드의 별도 트랜잭션이라 여기서 실패해도 200은 이미 나간 뒤다.
       * 그래서 confirm 200이 아니라 배달시작 SSE 수신을 기준으로 센다.
       * 드리미 쪽(deliveryDreamiAt)은 조건에 넣지 않는다 — 브라우저 드리미가 이긴 주문은
       * 원장이 그 SSE를 볼 수 없어(winnerDreamiEmail === null) 정상 건이 미완주로 잡힌다.
       */
      const completedCount = [...orders.values()].filter(
        (o) => o.confirmOk && o.deliveryBoormiAt !== null,
      ).length;

      for (const rec of orders.values()) {
        if (rec.createdAt === null) {
          const entry = { orderId: rec.orderId, offers: rec.offers.length };
          (external.has(rec.orderId) ? browserOrders : orphanOrders).push(entry);
          continue;
        }

        // 1. 오퍼 미발송
        if (rec.offers.length === 0) {
          if (matchedCount >= dreamiCount) {
            capacityBlocked.push({ orderId: rec.orderId, boormiEmail: rec.boormiEmail });
          } else {
            judge(missing, rec.createdAt, TIMEOUTS.offer, {
              orderId: rec.orderId,
              stage: "오퍼_미발송",
              target: rec.boormiEmail,
              since: rec.createdAt,
            });
          }
          continue;
        }

        // 승자는 dreami_info가 지목한 오퍼. accept 200(제출)으로 정하지 않는다.
        const winner = rec.offers.find((o) => o.offerId === rec.winnerOfferId) ?? null;
        // 수락을 제출한 것 중 가장 이른 시각 — dreami_info를 기다리기 시작한 시점.
        const firstSubmitAt = rec.offers
          .filter((o) => o.acceptOk === true)
          .reduce((min, o) => (min === null || o.acceptAt < min ? o.acceptAt : min), null);

        // 2. dreami_info 유실 — 수락을 제출했는데 부르미에게 아무 통지도 가지 않은 경우
        if (firstSubmitAt !== null && rec.dreamiInfoAt === null) {
          judge(missing, firstSubmitAt, TIMEOUTS.info, {
            orderId: rec.orderId,
            stage: "dreami_info_유실",
            target: rec.boormiEmail,
            since: firstSubmitAt,
          });
        }

        for (const offer of rec.offers) {
          if (offer === winner) continue;
          if (offer.closedAt !== null) continue;
          if (winner) {
            // 3. 형제 오퍼 마감 알림 유실.
            //    단 패배한 드리미가 offer_error("이미 다른 드리미가 수락한 주문입니다.")를
            //    받았다면 마감은 통지된 것이다 — 채널만 다르다.
            if (gotErrorAfter(offer.dreamiEmail, rec.dreamiInfoAt)) {
              closedViaError.push({ orderId: rec.orderId, dreamiEmail: offer.dreamiEmail });
              continue;
            }
            judge(missing, rec.dreamiInfoAt, TIMEOUTS.closed, {
              orderId: rec.orderId,
              stage: "offer_closed_유실",
              target: offer.dreamiEmail,
              since: rec.dreamiInfoAt,
            });
          } else if (offer.acceptOk !== true) {
            // 5. 만료 알림 유실 (오퍼 TTL 30초 + 여유)
            judge(missing, offer.popupAt, TIMEOUTS.expire, {
              orderId: rec.orderId,
              stage: "만료알림_유실",
              target: offer.dreamiEmail,
              since: offer.popupAt,
            });
          }
        }

        // 4. 배달 시작 알림 유실
        if (rec.confirmOk) {
          if (rec.deliveryDreamiAt === null) {
            // 승자가 브라우저 드리미면 이 원장은 그 오퍼의 offer_popup을 본 적이 없어
            // winnerDreamiEmail이 비어 있다. delivery_started_dreami는 승자 브라우저로 가므로
            // 부하 에이전트 SSE에는 영영 오지 않는다 — 유실이 아니라 관측 범위 밖이다.
            if (rec.winnerDreamiEmail === null) {
              externalWinnerOrders.push({ orderId: rec.orderId, boormiEmail: rec.boormiEmail });
            } else {
              judge(missing, rec.confirmAt, TIMEOUTS.delivery, {
                orderId: rec.orderId,
                stage: "delivery_started_dreami_유실",
                target: rec.winnerDreamiEmail,
                since: rec.confirmAt,
              });
            }
          }
          if (rec.deliveryBoormiAt === null) {
            judge(missing, rec.confirmAt, TIMEOUTS.delivery, {
              orderId: rec.orderId,
              stage: "delivery_started_boormi_유실",
              target: rec.boormiEmail,
              since: rec.confirmAt,
            });
          }
        }
      }

      // ── 지연 ──
      // 서버 구간은 "요청 발신 → SSE 도착"이다. 응답 수신을 기준점으로 삼으면 API 왕복이 어느 지표에도
      // 잡히지 않고 사라지며, 엔진이 보낸 SSE가 응답보다 먼저 도착하는 정상 상황에서 구간이 음수가 된다.
      const server = {
        createReqToOffer: [],
        acceptReqToInfo: [],
        confirmReqToDeliveryBoormi: [],
        confirmReqToDeliveryDreami: [],
      };
      for (const rec of orders.values()) {
        if (rec.createReqAt === null) continue;
        const first = rec.offers[0];
        if (first) server.createReqToOffer.push(first.popupAt - rec.createReqAt);
        const winner = rec.offers.find((o) => o.offerId === rec.winnerOfferId);
        if (winner?.acceptReqAt && rec.dreamiInfoAt) {
          server.acceptReqToInfo.push(rec.dreamiInfoAt - winner.acceptReqAt);
        }
        if (rec.confirmReqAt !== null) {
          if (rec.deliveryBoormiAt) {
            server.confirmReqToDeliveryBoormi.push(rec.deliveryBoormiAt - rec.confirmReqAt);
          }
          if (rec.deliveryDreamiAt) {
            server.confirmReqToDeliveryDreami.push(rec.deliveryDreamiAt - rec.confirmReqAt);
          }
        }
      }

      // ── 드리미/부르미 분포 ──
      const byDreami = new Map();
      const byBoormi = new Map();
      for (const rec of orders.values()) {
        if (rec.createdAt === null) continue;
        const b = byBoormi.get(rec.boormiEmail) ?? { orders: 0, matched: 0, unmatched: [] };
        b.orders++;
        if (rec.confirmOk) b.matched++;
        else b.unmatched.push(rec.orderId);
        byBoormi.set(rec.boormiEmail, b);

        for (const offer of rec.offers) {
          const d = byDreami.get(offer.dreamiEmail) ?? { offers: 0, submitted: 0, won: 0, completed: 0 };
          d.offers++;
          if (offer.acceptOk === true) d.submitted++;
          // 선착순에서 실제로 이긴 오퍼만 센다.
          if (offer.offerId === rec.winnerOfferId) {
            d.won++;
            // 1절 완주와 같은 기준(배달시작 SSE 수신)으로 센다.
            if (rec.confirmOk && rec.deliveryBoormiAt !== null) d.completed++;
          }
          byDreami.set(offer.dreamiEmail, d);
        }
      }

      return {
        counters: { ...counters },
        completed: completedCount,
        offersSent: [...orders.values()].reduce((n, r) => n + r.offers.length, 0),
        won: [...orders.values()].filter((r) => r.winnerOfferId).length,
        missing,
        pending,
        capacityBlocked,
        closedViaError,
        orphanOrders,
        browserOrders,
        externalWinnerOrders,
        latency: {
          api: statsOf(rtt),
          server: statsOf(server),
        },
        byDreami: Object.fromEntries(byDreami),
        byBoormi: Object.fromEntries(byBoormi),
        dreamiCount,
        dreamisWithNoOffer: dreamiCount - byDreami.size,
        closedReasons: Object.fromEntries(closedReasons),
        errors: {
          create: Object.fromEntries(createErrors),
          accept: Object.fromEntries(acceptErrors),
          confirm: Object.fromEntries(confirmErrors),
        },
        orderIds: [...orders.keys()],
        records: [...orders.values()],
      };
    },
  };
}

/**
 * 관측 결과와 DB를 주문별로 대조한다. 불일치만 돌려준다.
 * 특히 `ORDERS.DREAMI_ID == DELIVERY.DREAMI_ID == 원장이 본 수락자`가 아닌 건은 엉뚱한 배차다.
 */
export function reconcile({ records, dbRows, dreamiIdByEmail, duplicates }) {
  const byId = new Map(dbRows.map((r) => [String(r.ORDER_ID).replace(/-/g, "").toUpperCase(), r]));
  const mismatches = [];

  for (const rec of records) {
    const key = rec.orderId.replace(/-/g, "").toUpperCase();
    const row = byId.get(key);
    if (!row) {
      mismatches.push({ orderId: rec.orderId, kind: "DB에_주문없음" });
      continue;
    }
    if (!rec.confirmOk) {
      // 매칭이 성사되지 않은 주문. 확정까지 갔는데 DB가 멈춰 있는 경우만 문제로 본다.
      if (rec.dreamiInfoAt && row.ORDER_CD === "PENDING_BOORMI_CONFIRMATION") {
        mismatches.push({
          orderId: rec.orderId,
          kind: "부르미확정_미완료",
          detail: `ORDER_CD=${row.ORDER_CD} — dreami_info는 왔으나 확정이 끝나지 않음`,
        });
      }
      continue;
    }

    if (row.ORDER_CD !== "IN_PROGRESS") {
      mismatches.push({ orderId: rec.orderId, kind: "상태불일치", detail: `ORDER_CD=${row.ORDER_CD}` });
    }
    if (!row.ACCEPTED_DTM) {
      mismatches.push({ orderId: rec.orderId, kind: "MATCHING_없음" });
    }
    if (!row.DELIVERY_CD) {
      mismatches.push({ orderId: rec.orderId, kind: "DELIVERY_없음" });
    }

    const expected = dreamiIdByEmail.get(rec.winnerDreamiEmail);
    const norm = (v) => (v == null ? null : String(v).replace(/-/g, "").toUpperCase());
    if (expected && norm(row.ORDER_DREAMI_ID) !== norm(expected)) {
      mismatches.push({
        orderId: rec.orderId,
        kind: "배차_드리미_불일치",
        detail: `관측 ${rec.winnerDreamiEmail} 인데 ORDERS.DREAMI_ID=${row.ORDER_DREAMI_ID}`,
      });
    }
    if (row.DELIVERY_DREAMI_ID && norm(row.DELIVERY_DREAMI_ID) !== norm(row.ORDER_DREAMI_ID)) {
      mismatches.push({
        orderId: rec.orderId,
        kind: "ORDERS와_DELIVERY_드리미_불일치",
        detail: `${row.ORDER_DREAMI_ID} vs ${row.DELIVERY_DREAMI_ID}`,
      });
    }
  }

  for (const dup of duplicates ?? []) {
    mismatches.push({
      orderId: "-",
      kind: "중복배차",
      detail: `드리미 ${dup.DREAMI_ID}가 IN_PROGRESS 주문 ${dup.CNT}건을 동시에 보유`,
    });
  }

  return mismatches;
}

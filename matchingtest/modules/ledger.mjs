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
 *  6. pickup-finish 200     → 부르미에게 delivery_delivering          (T_DELIVERING)
 *  7. finish 200            → 부르미에게 delivery_completed           (T_COMPLETED)
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
  delivering: Number(process.env.T_DELIVERING ?? 10_000),
  completed: Number(process.env.T_COMPLETED ?? 10_000),
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

/**
 * 진행 중 배달 수를 1초 해상도로 재구성한다. 이 테스트의 핵심 산출물.
 *
 * 한 주문이 동시 배달 수에 기여하는 구간은 [배달 시작 SSE 수신, 배달 완료 응답]이다.
 * 완료하지 못한 건은 끝을 관측 종료 시각으로 둔다 — 끝난 적이 없으니 끝까지 붙잡고 있던 게 맞다.
 *
 * 구간마다 차분 배열에 ±1만 찍고 한 번 누적합한다. 건수가 늘어도 비용이 구간 수에 비례한다.
 */
function buildConcurrency(records, finishedAt) {
  const spans = records
    .filter((r) => r.driving && r.deliveryDreamiAt !== null)
    .map((r) => [r.deliveryDreamiAt, r.finishOk === true ? r.finishAt : finishedAt])
    .filter(([from, to]) => to >= from);
  if (spans.length === 0) return { origin: null, series: [], peak: 0, peakAtMs: null, at: null };

  let origin = Infinity;
  let end = -Infinity;
  for (const [from, to] of spans) {
    if (from < origin) origin = from;
    if (to > end) end = to;
  }
  const len = Math.floor((end - origin) / 1000) + 1;
  const diff = new Int32Array(len + 1);
  for (const [from, to] of spans) {
    diff[Math.floor((from - origin) / 1000)] += 1;
    diff[Math.min(len, Math.floor((to - origin) / 1000) + 1)] -= 1;
  }
  const series = new Array(len);
  let cur = 0;
  let peak = 0;
  let peakIdx = 0;
  for (let i = 0; i < len; i++) {
    cur += diff[i];
    series[i] = cur;
    if (cur > peak) {
      peak = cur;
      peakIdx = i;
    }
  }
  const at = (t) => series[Math.min(len - 1, Math.max(0, Math.floor((t - origin) / 1000)))];
  return { origin, series, peak, peakAtMs: peakIdx * 1000, at };
}

/** 동시 배달 수 구간의 경계. 마지막 값은 "이상"으로 연다. */
const CONCURRENCY_EDGES = [0, 10, 25, 50, 100, 200, 400, 800, 1600];

/**
 * 각 요청을 "그 요청을 보낸 순간의 동시 배달 수"로 버킷팅해 구간별 지연·실패를 낸다.
 * 몇 건부터 무너지는지를 직접 읽는 표라서, 전체 p95 하나보다 이 표가 결론에 가깝다.
 */
function buildConcurrencyBands(samples, concurrency) {
  if (!concurrency.at) return [];
  const last = CONCURRENCY_EDGES.length - 1;
  const label = (i) =>
    i === last ? `${CONCURRENCY_EDGES[i]}+` : `${CONCURRENCY_EDGES[i]}-${CONCURRENCY_EDGES[i + 1] - 1}`;
  const indexOf = (n) => {
    let i = 0;
    while (i < last && n >= CONCURRENCY_EDGES[i + 1]) i++;
    return i;
  };

  const rows = CONCURRENCY_EDGES.map((_, i) => ({
    band: label(i),
    total: 0,
    fail: 0,
    location: [],
    pickupFinish: [],
    finish: [],
  }));
  for (const [key, list] of Object.entries(samples)) {
    for (const s of list) {
      const row = rows[indexOf(concurrency.at(s.reqAt))];
      row.total++;
      if (s.ok) row[key].push(s.ms);
      else row.fail++;
    }
  }
  return rows
    .filter((r) => r.total > 0)
    .map((r) => ({
      band: r.band,
      total: r.total,
      fail: r.fail,
      location: stats(r.location),
      pickupFinish: stats(r.pickupFinish),
      finish: stats(r.finish),
    }));
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

    // ── 배달 단계 ──
    /** 배달을 실제로 구동하기 시작한 주문 수(= delivery_started_dreami를 부하 드리미가 받은 수). */
    deliveryStarted: 0,
    locationOk: 0,
    locationFail: 0,
    presignOk: 0,
    presignFail: 0,
    uploadOk: 0,
    uploadFail: 0,
    pickupOk: 0,
    pickupFail: 0,
    finishOk: 0,
    finishFail: 0,
    /** 부르미 SSE로 관측한 건수. 서버가 실제로 통지를 뿌렸는지의 근거. */
    locationSse: 0,
    deliveringSse: 0,
    completedSse: 0,
    /** 30초 넘게 GPS가 끊겼다고 서버가 판단한 통지. 하네스가 밀렸거나 서버가 밀렸다는 신호. */
    dreamiOffline: 0,
  };
  /** 엔드포인트별 API 왕복 시간. 톰캣 큐 + 트랜잭션 + DB까지의 동기 서버 처리 시간이다. */
  const rtt = {
    create: [],
    accept: [],
    confirm: [],
    location: [],
    presign: [],
    upload: [],
    pickupFinish: [],
    finish: [],
  };
  /**
   * 동시 배달 수 구간별로 다시 쪼갤 표본. rtt와 달리 요청 발신 시각을 같이 들고 있어야
   * "그 순간 몇 건이 동시에 돌고 있었는가"로 버킷팅할 수 있다.
   */
  const samples = { location: [], pickupFinish: [], finish: [] };
  /** 드리미 이메일 → offer_error 수신 시각들. 형제 오퍼 마감을 이걸로 알게 된 경우를 가린다. */
  const offerErrorsByDreami = new Map();
  const closedReasons = new Map();
  const createErrors = new Map();
  const acceptErrors = new Map();
  const confirmErrors = new Map();
  /** 배달 단계 에러. `단계: 메시지` 한 키로 모아 어느 단계가 무너졌는지 바로 보이게 한다. */
  const deliveryErrors = new Map();

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

        // ── 배달 단계 ──
        // deliveryDreamiAt이 배달 구동 시작 시각이고, finishAt(없으면 관측 종료)이 끝이다.
        // 이 구간이 곧 "이 주문이 동시 배달 수에 기여한 구간"이다.
        driving: false,
        locationOk: 0,
        locationFail: 0,
        pickupReqAt: null,
        pickupAt: null,
        pickupOk: null,
        finishReqAt: null,
        finishAt: null,
        finishOk: null,
        deliveringSseAt: null,
        completedSseAt: null,
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

    // ── 배달 단계 ──────────────────────────────────────────────────

    /** 배달 구동 시작. deliveryDreamiAt이 이미 시작 시각이라 여기서는 표시만 남긴다. */
    deliveryDriveStart(orderId) {
      const rec = ensureOrder(orderId);
      if (rec.driving) return;
      rec.driving = true;
      counters.deliveryStarted++;
    },

    /** 드리미 위치 전송 1회. 진행 중 배달 수 × (1/주기)만큼 지속적으로 쌓이는 주 부하다. */
    locationPing({ orderId, ok, reqAt, at, error }) {
      const rec = ensureOrder(orderId);
      const elapsed = at - reqAt;
      rtt.location.push(elapsed);
      samples.location.push({ reqAt, ms: elapsed, ok });
      if (ok) {
        counters.locationOk++;
        rec.locationOk++;
      } else {
        counters.locationFail++;
        rec.locationFail++;
        if (error) bump(deliveryErrors, `location: ${error.slice(0, 140)}`);
      }
    },

    /** presign 발급(`GET /upload/url`). */
    presignResult({ ok, reqAt, at, error }) {
      rtt.presign.push(at - reqAt);
      if (ok) counters.presignOk++;
      else {
        counters.presignFail++;
        if (error) bump(deliveryErrors, `presign: ${error.slice(0, 140)}`);
      }
    },

    /** presign URL로의 실제 PUT. 로컬은 DevStorageController(인메모리)가 받는다. */
    uploadResult({ ok, reqAt, at, error }) {
      rtt.upload.push(at - reqAt);
      if (ok) counters.uploadOk++;
      else {
        counters.uploadFail++;
        if (error) bump(deliveryErrors, `upload: ${error.slice(0, 140)}`);
      }
    },

    pickupFinishResult({ orderId, ok, reqAt, at, error }) {
      const rec = ensureOrder(orderId);
      const elapsed = at - reqAt;
      rtt.pickupFinish.push(elapsed);
      samples.pickupFinish.push({ reqAt, ms: elapsed, ok });
      rec.pickupReqAt = reqAt;
      rec.pickupAt = at;
      rec.pickupOk = ok;
      if (ok) counters.pickupOk++;
      else {
        counters.pickupFail++;
        if (error) bump(deliveryErrors, `pickup-finish: ${error.slice(0, 140)}`);
      }
    },

    finishResult({ orderId, ok, reqAt, at, error }) {
      const rec = ensureOrder(orderId);
      const elapsed = at - reqAt;
      rtt.finish.push(elapsed);
      samples.finish.push({ reqAt, ms: elapsed, ok });
      rec.finishReqAt = reqAt;
      rec.finishAt = at;
      rec.finishOk = ok;
      if (ok) counters.finishOk++;
      else {
        counters.finishFail++;
        if (error) bump(deliveryErrors, `finish: ${error.slice(0, 140)}`);
      }
    },

    /** 배달이 진행 중이 아닌데도 더 갈 수 없어 중단된 경우(취소·이미 완료 등). */
    deliveryAborted({ orderId, reason }) {
      ensureOrder(orderId);
      bump(deliveryErrors, `중단: ${String(reason).slice(0, 140)}`);
    },

    /** 부르미가 받은 배달 단계 SSE. */
    deliveryEvent({ orderId, kind, at }) {
      const rec = ensureOrder(orderId);
      if (kind === "location") counters.locationSse++;
      else if (kind === "delivering") {
        counters.deliveringSse++;
        rec.deliveringSseAt ??= at;
      } else if (kind === "completed") {
        counters.completedSse++;
        rec.completedSseAt ??= at;
      } else if (kind === "offline") {
        counters.dreamiOffline++;
      }
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

        // 6. 픽업 완료 통지 유실 — 드리미의 pickup-finish는 200인데 부르미가 못 받았다
        if (rec.pickupOk === true && rec.deliveringSseAt === null) {
          judge(missing, rec.pickupAt, TIMEOUTS.delivering, {
            orderId: rec.orderId,
            stage: "delivery_delivering_유실",
            target: rec.boormiEmail,
            since: rec.pickupAt,
          });
        }

        // 7. 배달 완료 통지 유실
        if (rec.finishOk === true && rec.completedSseAt === null) {
          judge(missing, rec.finishAt, TIMEOUTS.completed, {
            orderId: rec.orderId,
            stage: "delivery_completed_유실",
            target: rec.boormiEmail,
            since: rec.finishAt,
          });
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
        pickupReqToDelivering: [],
        finishReqToCompleted: [],
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
        if (rec.pickupReqAt !== null && rec.deliveringSseAt !== null) {
          server.pickupReqToDelivering.push(rec.deliveringSseAt - rec.pickupReqAt);
        }
        if (rec.finishReqAt !== null && rec.completedSseAt !== null) {
          server.finishReqToCompleted.push(rec.completedSseAt - rec.finishReqAt);
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

      // ── 동시 배달 ──
      const records = [...orders.values()];
      const concurrency = buildConcurrency(records, finishedAt);
      const concurrencyBands = buildConcurrencyBands(samples, concurrency);

      return {
        counters: { ...counters },
        completed: completedCount,
        /** 배달까지 실제로 끝난 건. 이 테스트의 완주 기준. */
        delivered: records.filter((r) => r.finishOk === true).length,
        concurrency: {
          peak: concurrency.peak,
          peakAtMs: concurrency.peakAtMs,
          origin: concurrency.origin,
          series: concurrency.series,
          bands: concurrencyBands,
        },
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
          delivery: Object.fromEntries(deliveryErrors),
        },
        orderIds: [...orders.keys()],
        records,
      };
    },
  };
}

/**
 * 관측 결과와 DB를 주문별로 대조한다. 불일치만 돌려준다.
 * 특히 `ORDERS.DREAMI_ID == DELIVERY.DREAMI_ID == 원장이 본 수락자`가 아닌 건은 엉뚱한 배차다.
 *
 * `browserDeliveredOrderIds`는 실클라이언트 창이 UI로 완주시킨 주문이다. 이 원장은 그 전이를
 * 보지 못해 `finishOk`가 비어 있는데 DB는 COMPLETED라, 넘기지 않으면 전부 `상태불일치`로 잡힌다.
 * 검증에서 빼는 게 아니라 하네스가 몬 건과 **같은 강도로** 완주 검증을 태운다.
 */
export function reconcile({ records, dbRows, dreamiIdByEmail, duplicates, browserDeliveredOrderIds }) {
  const idKey = (v) => String(v).replace(/-/g, "").toUpperCase();
  const byId = new Map(dbRows.map((r) => [idKey(r.ORDER_ID), r]));
  const browserDelivered = new Set((browserDeliveredOrderIds ?? []).map(idKey));
  const mismatches = [];

  for (const rec of records) {
    const key = idKey(rec.orderId);
    const row = byId.get(key);
    if (!row) {
      mismatches.push({ orderId: rec.orderId, kind: "DB에_주문없음" });
      continue;
    }
    // 브라우저 부르미의 주문은 확정도 브라우저가 하므로 원장에 confirmOk가 없다.
    // 그래도 브라우저 드리미가 완주시킨 건이면 아래 완주 검증까지 그대로 태운다.
    const finishedByBrowser = browserDelivered.has(key);
    if (!rec.confirmOk && !finishedByBrowser) {
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

    if (rec.finishOk === true || finishedByBrowser) {
      // 배달까지 끝낸 건. 주문·배달 상태, 인증 사진, 정산이 전부 반영돼야 정상이다.
      if (row.ORDER_CD !== "COMPLETED") {
        mismatches.push({ orderId: rec.orderId, kind: "주문_미완료", detail: `ORDER_CD=${row.ORDER_CD}` });
      }
      if (row.DELIVERY_CD !== "DELIVERED") {
        mismatches.push({
          orderId: rec.orderId,
          kind: "배달상태_불일치",
          detail: `DELIVERY_CD=${row.DELIVERY_CD}`,
        });
      }
      if (!row.PICKUP_CERT_ID) {
        mismatches.push({ orderId: rec.orderId, kind: "픽업인증_없음" });
      }
      if (!row.DELIVERY_CERT_ID) {
        mismatches.push({ orderId: rec.orderId, kind: "배달인증_없음" });
      }
      // 결제 시점에 PENDING으로 만들어지고 settleOrder에서 PAID가 된다. PENDING이면 정산이 안 돈 것이다.
      if (row.POINT_TX_STATUS !== "PAID") {
        mismatches.push({
          orderId: rec.orderId,
          kind: "정산_미반영",
          detail: `POINT_TX.status=${row.POINT_TX_STATUS ?? "없음"}`,
        });
      }
    } else if (row.ORDER_CD !== "IN_PROGRESS") {
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

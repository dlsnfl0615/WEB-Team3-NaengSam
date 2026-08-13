/**
 * 실경로 부하 드라이버.
 *
 * 디버그 API를 쓰지 않는다. 시드 계정으로 실제 로그인해서 세션·SSE·주문 생성·수락·확정을 전부
 * 운영과 같은 경로로 태우고, 오가는 이벤트를 전부 원장(ledger.mjs)에 적는다.
 *
 *   로그인(JSESSIONID) → GET /api/v1/sse/subscribe
 *   드리미: POST /api/v1/dreami/status/online
 *           offer_popup 수신 → POST /api/v1/dreami/offers/{offerId}/accept
 *   부르미: POST /api/v1/boormi/calls (주문 생성)
 *           dreami_info 수신 → POST /api/v1/boormi/calls/{orderId}/confirm-dreami
 *
 * 완주 상한은 드리미 계정 수와 같다. 매칭이 성사되면 주문이 IN_PROGRESS가 되고
 * `DreamiService.goOnline`의 `countActiveOrders > 0` 가드에 걸려 그 계정은 다시 온라인이 될 수 없다.
 *
 * DELIVERY=1이면 배달 시작 SSE를 받은 드리미가 그대로 배달까지 몰고 간다(delivery.mjs).
 * 이 경우 관측이 끝나는 조건은 "매칭 확정 완료"가 아니라 "진행 중 배달 0건"이다.
 */

import { runDelivery } from "./delivery.mjs";

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

/** 같은 실패가 수백 건 쏟아지므로 종류별 첫 건만 본문을 남긴다. */
function firstOnly() {
  const seen = new Set();
  return (kind, message, emit) => {
    if (seen.has(kind)) return;
    seen.add(kind);
    emit(`첫 ${kind}: ${message}`);
  };
}

/** 작업 목록을 동시 실행 수 제한을 지켜가며 돌린다. 실패는 세기만 하고 전체를 멈추지 않는다. */
export async function runLimited(items, limit, worker, onError) {
  let cursor = 0;
  let failed = 0;
  const runners = Array.from({ length: Math.max(1, Math.min(limit, items.length)) }, async () => {
    for (;;) {
      const index = cursor++;
      if (index >= items.length) return;
      try {
        await worker(items[index], index);
      } catch (e) {
        failed++;
        onError?.(e);
      }
    }
  });
  await Promise.all(runners);
  return failed;
}

/** `event:`/`data:` 라인만 보는 최소 SSE 파서. data가 없는 블록(주석 등)은 버린다. */
export function parseEvent(block) {
  let name = "message";
  const dataLines = [];
  for (const line of block.split("\n")) {
    if (line.startsWith("event:")) name = line.slice(6).trim();
    else if (line.startsWith("data:")) dataLines.push(line.slice(5).trim());
  }
  if (dataLines.length === 0) return null;
  const raw = dataLines.join("\n");
  try {
    return { name, data: JSON.parse(raw) };
  } catch {
    return { name, data: raw };
  }
}

export function createClient(base) {
  /** 응답 envelope({result}) 또는 raw를 모두 받아준다. */
  async function call(agent, method, path, body) {
    const res = await fetch(`${base}${path}`, {
      method,
      headers: {
        Cookie: agent.cookie,
        ...(body === undefined ? {} : { "Content-Type": "application/json" }),
      },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    const text = await res.text();
    if (!res.ok) throw new Error(`${method} ${path} → ${res.status} ${text}`);
    if (!text) return null;
    const parsed = JSON.parse(text);
    return parsed && typeof parsed === "object" && "result" in parsed ? parsed.result : parsed;
  }

  /** fetch는 쿠키를 자동으로 관리하지 않으므로 JSESSIONID를 직접 뽑아 이후 요청에 실어 보낸다. */
  async function login(agent) {
    const res = await fetch(`${base}/api/v1/user/login`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email: agent.email, password: agent.password }),
    });
    if (!res.ok) throw new Error(`로그인 실패 ${agent.email} → ${res.status} ${await res.text()}`);
    const setCookie = res.headers.getSetCookie().find((c) => c.startsWith("JSESSIONID="));
    if (!setCookie) throw new Error(`로그인 응답에 JSESSIONID가 없음: ${agent.email}`);
    agent.cookie = setCookie.split(";")[0];
  }

  /**
   * SSE 구독. 서버가 연결 직후 `connected` 핸드셰이크를 보내므로 그 첫 이벤트에서 resolve한다 —
   * 온라인 전환을 그 뒤에 해야 첫 오퍼를 놓치지 않는다.
   */
  function subscribe(agent, onEvent, onDrop) {
    const controller = new AbortController();
    agent.controller = controller;

    return new Promise((resolve, reject) => {
      (async () => {
        let opened = false;
        try {
          const res = await fetch(`${base}/api/v1/sse/subscribe`, {
            headers: { Cookie: agent.cookie, Accept: "text/event-stream" },
            signal: controller.signal,
          });
          if (!res.ok) throw new Error(`SSE 구독 실패 ${agent.email} → ${res.status}`);

          const reader = res.body.pipeThrough(new TextDecoderStream()).getReader();
          let buffer = "";
          for (;;) {
            const { value, done } = await reader.read();
            if (done) break;
            buffer += value;
            for (;;) {
              const boundary = buffer.indexOf("\n\n");
              if (boundary === -1) break;
              const block = buffer.slice(0, boundary);
              buffer = buffer.slice(boundary + 2);
              const event = parseEvent(block);
              if (!event) continue;
              if (!opened) {
                opened = true;
                resolve();
              }
              onEvent(event);
            }
          }
          if (opened) onDrop?.(new Error(`SSE 스트림 종료 ${agent.email}`));
        } catch (e) {
          if (controller.signal.aborted) return;
          if (opened) onDrop?.(e);
          else reject(e);
        }
      })();
    });
  }

  return { call, login, subscribe };
}

/**
 * 부하 실행. 진행 상황은 `log`(단발 메시지)와 `tick`(주기 요약)으로 밖에 넘긴다.
 * 반환 시점에는 SSE가 모두 끊기고 드리미가 오프라인으로 되돌려진 상태다.
 */
export async function runDrive({ ledger, dreamis, boormis, config, log, tick }) {
  const { call, login, subscribe } = createClient(config.apiBase);
  const noteFirst = firstOnly();
  const note = (kind, e) => noteFirst(kind, e.message, log);

  const state = { online: 0, created: 0, dispatched: 0 };
  /** 진행 중 배달의 Promise. 크기가 곧 실시간 동시 배달 수다. */
  const activeDeliveries = new Set();
  /** 제한 시간이 다 되어 남은 배달을 강제로 끊는 중인지. */
  let cutOff = false;

  // ── 1. 로그인 ──
  const all = [...dreamis, ...boormis];
  const loginFailed = await runLimited(all, config.loginConcurrency, login, (e) =>
    note("로그인 실패", e),
  );
  log(`로그인 ${all.length - loginFailed}/${all.length}` + (loginFailed ? ` (실패 ${loginFailed})` : ""));

  const liveDreamis = dreamis.filter((a) => a.cookie);
  const liveBoormis = boormis.filter((a) => a.cookie);
  if (liveDreamis.length === 0 || liveBoormis.length === 0) {
    throw new Error("로그인에 성공한 드리미 또는 부르미가 없습니다 — 시드 계정을 확인하세요.");
  }

  // ── 2. 부르미 SSE 먼저 ──
  // 드리미가 온라인이 되는 순간 오퍼가 나가고 곧바로 dreami_info가 뒤따르는데, 그때 부르미 SSE가
  // 아직 없으면 그 이벤트는 조용히 버려진다(SseEmitterRegistry는 미연결 사용자에게 보내지 않는다).
  const boormiSubFailed = await runLimited(
    liveBoormis,
    config.loginConcurrency,
    (agent) =>
      subscribe(
        agent,
        (event) => onBoormiEvent(agent, event),
        (e) => note("부르미 SSE 끊김", e),
      ),
    (e) => note("부르미 SSE 실패", e),
  );
  log(
    `부르미 SSE ${liveBoormis.length - boormiSubFailed}/${liveBoormis.length}` +
      (boormiSubFailed ? ` (실패 ${boormiSubFailed})` : ""),
  );

  // ── 3. 드리미 SSE + 온라인 ──
  const onlineFailed = await runLimited(
    liveDreamis,
    config.loginConcurrency,
    async (agent) => {
      await subscribe(
        agent,
        (event) => onDreamiEvent(agent, event),
        (e) => note("드리미 SSE 끊김", e),
      );
      await call(agent, "POST", "/api/v1/dreami/status/online", {
        latitude: agent.lat,
        longitude: agent.lng,
      });
      state.online++;
    },
    (e) => note("온라인 전환 실패", e),
  );
  log(
    `드리미 온라인 ${state.online}/${liveDreamis.length}` +
      (onlineFailed ? ` (실패 ${onlineFailed})` : ""),
  );

  // ── 이벤트 처리 ──
  // 요청 발신 시각(reqAt)을 응답 시각과 함께 원장에 넘긴다. 응답 시각만 남기면 서버가 비동기로 보낸 SSE가
  // 응답보다 먼저 도착했을 때 구간이 음수가 되고, API 왕복 시간도 어느 지표에도 분리되지 않는다.
  async function acceptOffer(agent, offerId) {
    if (config.acceptDelayMs > 0) await sleep(config.acceptDelayMs);
    const reqAt = Date.now();
    try {
      await call(agent, "POST", `/api/v1/dreami/offers/${offerId}/accept`);
      ledger.acceptResult({ offerId, ok: true, reqAt, at: Date.now() });
    } catch (e) {
      ledger.acceptResult({ offerId, ok: false, reqAt, at: Date.now(), error: e.message });
      note("수락 실패", e);
    }
  }

  async function confirmDreami(agent, orderId, offerId) {
    const reqAt = Date.now();
    try {
      await call(agent, "POST", `/api/v1/boormi/calls/${orderId}/confirm-dreami`, { offerId });
      ledger.confirmResult({ orderId, ok: true, reqAt, at: Date.now() });
    } catch (e) {
      ledger.confirmResult({ orderId, ok: false, reqAt, at: Date.now(), error: e.message });
      note("확정 실패", e);
    }
  }

  /**
   * 배달 구동 시작. 배달 시작 SSE를 받은 그 드리미가 이미 살아 있는 세션과 orderId를 쥐고 있으므로
   * 추가 로그인 없이 그대로 몰면 된다(upload/url의 소유자 검증도 이 세션이라야 통과한다).
   */
  function startDelivery(agent, orderId) {
    const task = runDelivery({
      agent,
      orderId,
      ledger,
      config,
      call,
      note,
      isStopping: () => stopping || cutOff,
    })
      .catch((e) => note("배달 구동 오류", e))
      .finally(() => activeDeliveries.delete(task));
    activeDeliveries.add(task);
  }

  function onDreamiEvent(agent, { name, data }) {
    switch (name) {
      case "offer_popup":
        ledger.offerPopup({
          orderId: data.orderId,
          offerId: data.offerId,
          dreamiEmail: agent.email,
          at: Date.now(),
        });
        void acceptOffer(agent, data.offerId);
        break;
      case "offer_closed":
        ledger.offerClosed({ offerId: data.offerId, reason: data.reason, at: Date.now() });
        break;
      case "delivery_started_dreami":
        ledger.deliveryStarted({ orderId: data.orderId, side: "dreami", at: Date.now() });
        if (config.delivery) startDelivery(agent, data.orderId);
        break;
      case "boormi_rejected":
        ledger.boormiRejected();
        break;
      case "offer_error":
        ledger.offerError({ dreamiEmail: agent.email, at: Date.now() });
        break;
      default:
        break;
    }
  }

  function onBoormiEvent(agent, { name, data }) {
    switch (name) {
      case "dreami_info":
        ledger.dreamiInfo({ orderId: data.orderId, offerId: data.offerId, at: Date.now() });
        void confirmDreami(agent, data.orderId, data.offerId);
        break;
      case "delivery_started_boormi":
        ledger.deliveryStarted({ orderId: data.orderId, side: "boormi", at: Date.now() });
        break;
      case "delivery_location":
        ledger.deliveryEvent({ orderId: data.orderId, kind: "location", at: Date.now() });
        break;
      case "delivery_delivering":
        ledger.deliveryEvent({ orderId: data.orderId, kind: "delivering", at: Date.now() });
        break;
      case "delivery_completed":
        ledger.deliveryEvent({ orderId: data.orderId, kind: "completed", at: Date.now() });
        break;
      // payload가 DreamiOfflineDto라 형태가 다르다. 30초 넘게 위치가 안 왔다는 서버 판정이므로
      // 하네스가 밀렸거나 서버가 밀렸다는 신호로 센다.
      case "delivery_dreami_offline":
        ledger.deliveryEvent({ orderId: data.orderId, kind: "offline", at: Date.now() });
        break;
      default:
        break;
    }
  }

  // ── 4. 주문 생성 램프 ──
  // `POST /boormi/calls`는 카카오 지오코딩 2회 + 길찾기 1회를 탄다. 초당 생성 수를 제한하지 않으면
  // 매칭이 아니라 외부 호출에서 먼저 막힌다.
  async function createOrder(agent) {
    ledger.createAttempt();
    const reqAt = Date.now();
    try {
      const orderId = await call(agent, "POST", "/api/v1/boormi/calls", agent.order);
      ledger.createOk(orderId, agent.email, reqAt, Date.now());
      state.created++;
    } catch (e) {
      ledger.createFail(e.message);
      note("주문 생성 실패", e);
    }
  }

  const intervalMs = 1000 / Math.max(1, config.orderRate);
  const inFlight = new Set();
  const rampStart = Date.now();

  for (let i = 0; i < config.orderCount; i++) {
    if (stopping) break;
    const agent = liveBoormis[i % liveBoormis.length];
    const task = createOrder(agent).finally(() => inFlight.delete(task));
    inFlight.add(task);
    state.dispatched = i + 1;
    const nextAt = rampStart + (i + 1) * intervalMs;
    const wait = nextAt - Date.now();
    if (wait > 0) await sleep(wait);
  }
  await Promise.all([...inFlight]);
  log(`주문 생성 ${state.created}/${config.orderCount}건 완료`);

  // ── 5. 소진 대기 ──
  // 완주 상한(= 드리미 계정 수)에 닿고 진행 중 배달이 다 빠지거나, DURATION_MS가 지나면 멈춘다.
  const cap = Math.min(config.orderCount, state.online);
  const deadline = Date.now() + config.durationMs;
  while (
    !stopping &&
    Date.now() < deadline &&
    (ledger.counters.confirmOk < cap || activeDeliveries.size > 0)
  ) {
    await sleep(500);
    tick?.(snapshotStats());
  }

  // 제한 시간에 걸린 배달은 여기서 정리한다. SSE를 먼저 끊으면 아직 살아 있는 배달이
  // 응답 없는 서버를 두드리는 꼴이 되고, 그 실패가 리포트에 서버 실패로 섞인다.
  if (activeDeliveries.size > 0) {
    log(`제한 시간 도달 — 진행 중 배달 ${activeDeliveries.size}건 중단`);
    cutOff = true;
    await Promise.allSettled([...activeDeliveries]);
  }

  // 마지막 단계의 타임아웃이 끝날 시간을 준다 — 이 시간이 없으면 정상 이벤트를 유실로 오판한다.
  log(`드레인 ${Math.round(config.drainMs / 1000)}초 — 남은 이벤트 수신 대기`);
  const drainUntil = Date.now() + config.drainMs;
  while (Date.now() < drainUntil) {
    await sleep(500);
    tick?.(snapshotStats());
  }

  // ── 6. 정리 ──
  for (const agent of all) agent.controller?.abort();
  // 이미 매칭된 드리미는 대기 목록에 없어 실패하는데, 그건 정상이므로 건별로 무시한다.
  await runLimited(liveDreamis, config.loginConcurrency, (agent) =>
    call(agent, "POST", "/api/v1/dreami/status/offline").catch(() => {}),
  );

  return { onlineDreamis: state.online, createdOrders: state.created };

  function snapshotStats() {
    const c = ledger.counters;
    return {
      created: state.created,
      dispatched: state.dispatched,
      target: config.orderCount,
      offers: [...ledger.orders.values()].reduce((n, r) => n + r.offers.length, 0),
      acceptSubmitted: c.acceptSubmitted,
      acceptFail: c.acceptFail,
      confirmOk: c.confirmOk,
      confirmFail: c.confirmFail,
      online: state.online,
      activeDeliveries: activeDeliveries.size,
      locationOk: c.locationOk,
      pickupOk: c.pickupOk,
      finishOk: c.finishOk,
      deliveryFail: c.pickupFail + c.finishFail + c.presignFail + c.uploadFail,
    };
  }
}

/** Ctrl+C 시 램프와 대기 루프를 조기 종료시킨다. */
let stopping = false;

export function requestStop() {
  stopping = true;
}

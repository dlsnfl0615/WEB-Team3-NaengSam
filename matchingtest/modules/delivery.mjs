/**
 * 배달 1건을 드리미 세션으로 완주시키는 드라이버.
 *
 * 매칭까지만 재는 부하는 서비스의 지속 부하를 거의 놓친다. 실제로 서버가 계속 얻어맞는 구간은
 * 배달이 시작된 뒤다 — 진행 중 배달 수 × (1/위치 전송 주기)만큼의 쓰기 요청이 끊임없이 들어오고,
 * 픽업/완료 전이는 주문 단위 비관적 쓰기 락을 잡으며, 완료 시점에는 정산 트랜잭션이 붙는다.
 *
 *   delivery_started_dreami 수신
 *     → POST /api/v1/delivery/orders/{orderId}/dreami-location   (LOCATION_INTERVAL_MS 주기)
 *     → (픽업 시각 도달) 인증 사진 업로드 → POST .../pickup-finish
 *     → (완료 시각 도달) 인증 사진 업로드 → POST .../finish
 *
 * 픽업 시각과 완료 시각은 건별로 설정 범위 안에서 균등분포로 뽑는다. 전부 같은 시각에 몰리면
 * 실제 서비스에 없는 동기화된 파도가 생겨 동시 배달 수 곡선이 계단처럼 튄다.
 *
 * ── 인증 사진에 대해 ──
 * 아무 문자열이나 photoKey로 넣으면 통과하지 않는다. `pickup-finish`/`finish`가 UploadSessionService로
 * (1) UPLOAD_SESSION row의 purpose·소유자·resourceId 일치, (2) 파일이 실제로 업로드됐는지까지 본다.
 * 그래서 발급→PUT→제출의 정식 경로를 그대로 타고, 사진 "내용"만 더미 1바이트로 둔다.
 *
 * 실패는 던지지 않고 전부 원장에 단계별로 적는다. 한 건이 죽어도 나머지 배달은 계속 돌아야 한다.
 */

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

/** 균등분포 난수. min > max로 잘못 설정돼도 뒤집어 쓴다. */
function between(min, max) {
  const lo = Math.min(min, max);
  const hi = Math.max(min, max);
  return lo + Math.random() * (hi - lo);
}

/**
 * 배달이 취소되었거나 이미 완료된 경우의 서버 코드. 실 클라이언트도 이걸 받으면 폴링을 멈춘다.
 * 이 두 가지는 서버가 밀려서 생긴 실패가 아니므로 계속 두드려 실패 건수를 부풀리면 안 된다.
 */
const TERMINAL_CODES = /DELIVERY_01[23]|DELIVERY_NOT_FOUND|DELIVERY_010/;

/** 업로드 본문. 내용은 검증하지 않으므로 최소 크기로 둔다 — 빈 본문은 역직렬화에서 걸린다. */
const DUMMY_BODY = new Uint8Array([0x89]);

/** 매번 같은 좌표를 보내면 서버가 위치 변화 경로를 타지 않아 실제보다 가벼워진다. */
function jitter() {
  return (Math.random() - 0.5) * 0.0005;
}

/**
 * 배달 1건을 끝까지 몬다. 정상/실패 어느 쪽이든 예외 없이 반환한다.
 *
 * @param call drive.mjs의 `createClient().call` — 쿠키를 실어 API_BASE에 요청한다.
 * @param isStopping Ctrl+C 등으로 조기 종료 중인지. true가 되면 폴링과 대기를 즉시 끊는다.
 */
export async function runDelivery({ agent, orderId, ledger, config, call, note, isStopping }) {
  ledger.deliveryDriveStart(orderId);

  const startedAt = Date.now();
  const pickupDueAt = startedAt + between(config.pickupMsMin, config.pickupMsMax);
  const finishDueAt = pickupDueAt + between(config.deliverMsMin, config.deliverMsMax);

  const state = { done: false, terminated: false };

  /** 목표 시각까지 잘게 나눠 기다린다. 조기 종료 요청에 반응하려면 한 번에 다 자면 안 된다. */
  async function waitUntil(target) {
    while (Date.now() < target) {
      if (state.terminated || isStopping()) return false;
      await sleep(Math.min(1000, target - Date.now()));
    }
    return !state.terminated && !isStopping();
  }

  /** presign 발급 → 실제 PUT → key 반환. 실패하면 null. */
  async function uploadPhoto(purpose, fileName) {
    const presignReqAt = Date.now();
    let issued;
    try {
      const query = `fileName=${fileName}&purpose=${purpose}&resourceId=${orderId}`;
      issued = await call(agent, "GET", `/api/v1/upload/url?${query}`);
      ledger.presignResult({ ok: true, reqAt: presignReqAt, at: Date.now() });
    } catch (e) {
      ledger.presignResult({ ok: false, reqAt: presignReqAt, at: Date.now(), error: e.message });
      note?.("presign 실패", e);
      return null;
    }

    // presign URL은 절대 URL이라 call()(API_BASE + path 전제)을 쓸 수 없다. 쿠키도 필요 없다.
    const putReqAt = Date.now();
    try {
      const res = await fetch(issued.url, {
        method: "PUT",
        headers: { "Content-Type": "image/png" },
        body: DUMMY_BODY,
      });
      if (!res.ok) throw new Error(`PUT ${res.status} ${(await res.text()).slice(0, 200)}`);
      ledger.uploadResult({ ok: true, reqAt: putReqAt, at: Date.now() });
    } catch (e) {
      ledger.uploadResult({ ok: false, reqAt: putReqAt, at: Date.now(), error: e.message });
      note?.("사진 업로드 실패", e);
      return null;
    }
    return issued.key;
  }

  /** 픽업/완료 전이 1회. 성공 여부만 돌려준다. */
  async function transition(path, photoKey, record, label) {
    const reqAt = Date.now();
    try {
      await call(agent, "POST", `/api/v1/delivery/orders/${orderId}/${path}`, { photoKey });
      record({ orderId, ok: true, reqAt, at: Date.now() });
      return true;
    } catch (e) {
      record({ orderId, ok: false, reqAt, at: Date.now(), error: e.message });
      if (TERMINAL_CODES.test(e.message)) state.terminated = true;
      note?.(`${label} 실패`, e);
      return false;
    }
  }

  async function locationLoop() {
    if (config.locationIntervalMs <= 0) return;
    // 경로·예상 도착시각은 첫 회에만 받는다(프론트와 같은 동작). 매번 true로 보내면
    // 서버가 좌표 배열을 계속 되돌려줘 실제보다 무거운 응답을 재게 된다.
    let includeRoute = true;
    while (!state.done && !state.terminated && !isStopping()) {
      const reqAt = Date.now();
      try {
        await call(agent, "POST", `/api/v1/delivery/orders/${orderId}/dreami-location`, {
          latitude: agent.lat + jitter(),
          longitude: agent.lng + jitter(),
          includeRoute,
        });
        ledger.locationPing({ orderId, ok: true, reqAt, at: Date.now() });
        includeRoute = false;
      } catch (e) {
        ledger.locationPing({ orderId, ok: false, reqAt, at: Date.now(), error: e.message });
        if (TERMINAL_CODES.test(e.message)) {
          state.terminated = true;
          ledger.deliveryAborted({ orderId, reason: "위치 전송이 취소/완료 응답을 받음" });
          return;
        }
        note?.("위치 전송 실패", e);
      }
      await sleep(config.locationIntervalMs);
    }
  }

  async function drive() {
    try {
      if (!(await waitUntil(pickupDueAt))) return;

      const pickupKey = await uploadPhoto("PICKUP_CERTIFICATION_IMAGE", "pickup.png");
      if (pickupKey === null) return;
      const pickedUp = await transition(
        "pickup-finish",
        pickupKey,
        ledger.pickupFinishResult,
        "픽업 완료",
      );
      // finish는 DELIVERING에서만 받는다. 픽업이 실패한 건을 계속 밀면 확정적으로 실패할 요청만 늘린다.
      if (!pickedUp) return;

      if (!(await waitUntil(finishDueAt))) return;

      const finishKey = await uploadPhoto("DELIVERY_CERTIFICATION_IMAGE", "delivered.png");
      if (finishKey === null) return;
      await transition("finish", finishKey, ledger.finishResult, "배달 완료");
    } finally {
      // 위치 폴링을 세우는 신호. drive가 끝나기 전에 세우면 폴링이 먼저 죽어 부하가 사라진다.
      state.done = true;
    }
  }

  await Promise.all([locationLoop(), drive()]);
}

/**
 * 부하테스트가 남긴 주문 정리기.
 *
 * 드라이버는 부르미 확정까지만 태우고 끝나므로 성사된 주문이 IN_PROGRESS + DELIVERY(PICKUP_NORMAL)로
 * 남는다. 활성 주문 하나가 부르미 1명과 드리미 1명을 동시에 잠그기 때문에(`pull.mjs`의
 * `NO_ACTIVE_ORDER`), 정리하지 않으면 몇 번 돌리는 사이 쓸 계정이 없어진다.
 *
 * DB는 건드리지 않는다. 취소는 전부 실제 API로 나간다.
 *
 *   MATCHING / PENDING_BOORMI_CONFIRMATION → DELETE /api/v1/boormi/calls/{orderId}
 *   IN_PROGRESS(PICKUP_NORMAL·PICKUP_DELAYED) → POST /api/v1/delivery/orders/{orderId}/cancel/boormi
 *
 * 두 API 모두 주문을 만든 부르미 본인의 세션을 요구한다(NOT_ORDER_OWNER / NOT_ORDER_BOORMI).
 * 관리자 취소(`cancel/admin`)는 권한 검증이 아직 없는 미완성 엔드포인트라 쓰지 않는다.
 */
import { createClient, runLimited } from "./drive.mjs";

/** 서버가 "이미 끝난 주문"이라고 답한 경우. 정리 목적으로는 성공과 같다. */
const CLOSED_MARKERS = [
  "DELIVERY_ALREADY_CANCELLED",
  "DELIVERY_ALREADY_COMPLETED",
  "DELIVERY_ALREADY_TERMINATED",
  "ORDER_NOT_FOUND",
  "DELIVERY_NOT_FOUND",
];

const isAlreadyClosed = (message) => CLOSED_MARKERS.some((code) => message.includes(code));

/**
 * 시도 순서. 상태를 알면 맞는 쪽을 먼저 치고, 모르면 매칭 전 취소부터 친다.
 * 어느 쪽이든 실패하면 나머지 하나로 폴백한다 — 조회와 취소 사이에 상태가 바뀔 수 있어서
 * 미리 조회해 한 쪽만 고르는 것보다 폴백 한 번이 싸고 정확하다.
 */
function attemptsFor(orderCd) {
  const unsubscribe = ["DELETE", (id) => `/api/v1/boormi/calls/${id}`];
  const cancelPickup = ["POST", (id) => `/api/v1/delivery/orders/${id}/cancel/boormi`];
  return orderCd === "IN_PROGRESS" ? [cancelPickup, unsubscribe] : [unsubscribe, cancelPickup];
}

/**
 * @param targets `[{ orderId, email, password, orderCd? }]` — orderCd는 있으면 시도 순서에만 쓴다.
 * @returns `{ cancelled, alreadyClosed, failed: [{ orderId, email, reason }] }`
 */
export async function cleanupOrders({ apiBase, targets, concurrency = 5, log = () => {} }) {
  const { call, login } = createClient(apiBase);

  // 부르미 한 명이 여러 주문을 들 수 있다(BoormiService.MAX_ACTIVE_ORDERS = 5).
  // 동시 실행이라 Promise를 캐시해야 같은 계정으로 로그인이 두 번 나가지 않는다.
  const sessions = new Map();
  const sessionFor = (email, password) => {
    let pending = sessions.get(email);
    if (!pending) {
      const agent = { email, password };
      pending = login(agent).then(() => agent);
      sessions.set(email, pending);
    }
    return pending;
  };

  const result = { cancelled: 0, alreadyClosed: 0, failed: [] };

  await runLimited(targets, concurrency, async (target) => {
    const { orderId, email, password, orderCd } = target;
    let agent;
    try {
      agent = await sessionFor(email, password);
    } catch (e) {
      result.failed.push({ orderId, email, reason: e.message });
      return;
    }

    const errors = [];
    for (const [method, path] of attemptsFor(orderCd)) {
      try {
        await call(agent, method, path(orderId));
        result.cancelled++;
        return;
      } catch (e) {
        if (isAlreadyClosed(e.message)) {
          result.alreadyClosed++;
          return;
        }
        errors.push(e.message);
      }
    }
    // 배달 중·완료·파트너 인계·반송 단계는 정상적으로 취소가 막힌다. 사유를 그대로 남긴다 —
    // 삼키면 그 계정이 왜 계속 잠겨 있는지 알 방법이 없다.
    result.failed.push({ orderId, email, reason: errors.join(" | ") });
  });

  log(
    `취소 ${result.cancelled}건, 이미 종료 ${result.alreadyClosed}건` +
      (result.failed.length ? `, 실패 ${result.failed.length}건` : ""),
  );
  return result;
}

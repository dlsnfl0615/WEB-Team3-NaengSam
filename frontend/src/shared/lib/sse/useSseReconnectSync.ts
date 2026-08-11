import { useEffect, useRef } from "react";
import type { SseStatus } from "./SseContext";

/** 일시 장애(reconnecting) 동안 상태를 다시 맞추는 폴링 간격. matching의 MATCHING_POLL_INTERVAL_MS와 동일값. */
export const SSE_RECONNECT_POLL_INTERVAL_MS = 3000;

export interface UseSseReconnectSyncOptions {
  /** false면 아무것도 하지 않는다(예: 상세 로드 전). */
  enabled: boolean;
}

/**
 * SSE가 끊겼다 다시 붙을 때 놓친 상태를 스냅샷 재조회로 복구하는 공용 훅.
 * matching이 `SseProvider`에서 전역 스토어로 하던 "reconnecting 동안 폴링 → connected 시 멈추고 마지막
 * 동기화"를, 상태가 화면 로컬인 곳(배달 추적 등)에서 쓰도록 컴포넌트 레벨로 일반화한 것이다.
 *
 * - `reconnecting` 동안 {@link SSE_RECONNECT_POLL_INTERVAL_MS}마다 `recover()`를 호출해 화면을 최신으로 유지한다.
 * - 한 번이라도 끊겼다(reconnecting/closed) `connected`로 복귀하면 `recover()`를 1회 호출한다.
 *   최초 마운트의 `connecting → connected`에는 호출하지 않는다(초기 로드는 이미 끝났으므로).
 *   이 방식은 수동 재연결(`closed → connecting → connected`)도 자연히 커버한다.
 *
 * `recover`는 매 렌더 새로 만들어질 수 있으므로 ref로 최신본을 보관한다.
 */
export function useSseReconnectSync(
  status: SseStatus,
  recover: () => void,
  { enabled }: UseSseReconnectSyncOptions,
): void {
  const recoverRef = useRef(recover);
  useEffect(() => {
    recoverRef.current = recover;
  });

  // 끊긴 적이 있는지 추적한다 — 최초 connecting→connected(마운트)와 재연결을 구분하기 위함.
  const hasDroppedRef = useRef(false);

  // 일시 장애 동안 주기적으로 스냅샷을 다시 맞춘다.
  useEffect(() => {
    if (!enabled || status !== "reconnecting") return;
    const id = window.setInterval(
      () => recoverRef.current(),
      SSE_RECONNECT_POLL_INTERVAL_MS,
    );
    return () => window.clearInterval(id);
  }, [enabled, status]);

  // 상태 전이를 관찰해 재연결 완료 시 1회 복구한다.
  useEffect(() => {
    if (!enabled) return;
    if (status === "reconnecting" || status === "closed") {
      hasDroppedRef.current = true;
    } else if (status === "connected" && hasDroppedRef.current) {
      hasDroppedRef.current = false;
      recoverRef.current();
    }
  }, [enabled, status]);
}

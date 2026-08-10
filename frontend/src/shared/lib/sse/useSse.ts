import { use, useEffect, useRef } from "react";
import { SseContext, type SseHandlers, type SseState } from "./SseContext";

export type { SseHandlers, SseState };

export interface UseSseOptions {
  /** false면 핸들러를 등록하지 않는다(예: 필수 파라미터가 아직 없을 때). 기본 true. */
  enabled?: boolean;
}

/**
 * 탭당 하나뿐인 공유 SSE 연결(`SseProvider`)에 이벤트 이름별 핸들러만 등록하는 훅.
 * 연결 자체는 만들지도 닫지도 않는다 — mount 시 등록하고 unmount 시 핸들러만 해제한다.
 *
 * `handlers`는 매 렌더 새로 생기므로 `ref`로 최신본을 보관해 불필요한 재등록을 피한다.
 */
export function useSse(handlers: SseHandlers, options: UseSseOptions = {}): SseState {
  const context = use(SseContext);
  if (!context) {
    throw new Error("useSse는 SseProvider 안에서만 사용할 수 있습니다.");
  }

  const { enabled = true } = options;
  const handlersRef = useRef(handlers);

  // 렌더 중이 아니라 커밋 후에 최신 핸들러를 보관한다(등록은 재생성하지 않고 dispatch만 최신화).
  useEffect(() => {
    handlersRef.current = handlers;
  });

  useEffect(() => {
    if (!enabled) return;

    const names = Object.keys(handlersRef.current);
    const unsubscribes = names.map((name) =>
      context.subscribe(name, (data) => {
        handlersRef.current[name]?.(data);
      }),
    );

    return () => {
      unsubscribes.forEach((unsubscribe) => unsubscribe());
    };
  }, [enabled, context]);

  return { connected: context.connected, status: context.status };
}

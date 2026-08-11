import { createContext } from "react";

/** SSE 이벤트 이름 → 파싱된 payload를 받는 핸들러. */
export type SseHandlers = Record<string, (data: unknown) => void>;

/**
 * SSE 연결 상태.
 * - `connecting`: 최초 연결 시도 중.
 * - `connected`: 핸드셰이크(connected) 완료.
 * - `reconnecting`: 일시 장애 — 브라우저가 자동 재연결하는 중(빠른 polling으로 상태 복구).
 * - `closed`: 영구 종료(예: 연결 한도 초과 204). 자동 재연결·무한 polling을 하지 않는다 — 수동 재연결만.
 */
export type SseStatus = "connecting" | "connected" | "reconnecting" | "closed";

export interface SseState {
  /** status === "connected" 와 동일한 편의 플래그. */
  connected: boolean;
  /** 세분화된 연결 상태. */
  status: SseStatus;
}

export interface SseContextValue extends SseState {
  /**
   * 이벤트 이름 하나에 핸들러 하나를 등록한다. 반환된 함수를 호출하면 등록을 해제한다
   * (탭당 하나뿐인 EventSource 자체는 건드리지 않는다).
   */
  subscribe: (eventName: string, handler: (data: unknown) => void) => () => void;
  /** 영구 종료(closed) 상태에서 사용자가 직접 연결을 다시 시도한다. */
  reconnect: () => void;
}

export const SseContext = createContext<SseContextValue | null>(null);

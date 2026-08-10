import { createContext } from "react";

/** SSE 이벤트 이름 → 파싱된 payload를 받는 핸들러. */
export type SseHandlers = Record<string, (data: unknown) => void>;

export interface SseState {
  /** 최초 핸드셰이크(connected) 이후 true. */
  connected: boolean;
}

export interface SseContextValue extends SseState {
  /**
   * 이벤트 이름 하나에 핸들러 하나를 등록한다. 반환된 함수를 호출하면 등록을 해제한다
   * (탭당 하나뿐인 EventSource 자체는 건드리지 않는다).
   */
  subscribe: (eventName: string, handler: (data: unknown) => void) => () => void;
}

export const SseContext = createContext<SseContextValue | null>(null);

import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { useSessionStore } from "@/shared/store/sessionStore";
import { SseContext, type SseContextValue } from "./SseContext";

export interface SseProviderProps {
  children: ReactNode;
}

type Listener = (data: unknown) => void;

/**
 * 탭(브라우저 컨텍스트)당 `EventSource`를 하나만 소유한다. 화면(`useSse`)들은 연결을 직접 만들지 않고
 * 이벤트 이름별 핸들러만 등록/해제한다 — 그래서 세션 하나(로그인 브라우저) : connection 하나(탭 하나)가
 * 실제로 성립한다(사용자당 최대 5개 = 동시에 열린 탭 최대 5개).
 *
 * 로그인 상태(`isAuthenticated`)가 꺼지면 연결을 닫는다. 명시적 로그아웃과 401 세션만료(자동 로그아웃)
 * 모두 결국 이 플래그를 false로 만들므로 별도 분기가 필요 없다.
 */
export function SseProvider({ children }: SseProviderProps) {
  const isAuthenticated = useSessionStore((state) => state.isAuthenticated);
  const [connected, setConnected] = useState(false);

  // 렌더와 무관하게 최신 등록 상태를 유지해야 하므로 ref로 보관한다(연결 자체는 재생성하지 않는다).
  const handlersByEventRef = useRef(new Map<string, Set<Listener>>());
  // 현재 EventSource에 실제로 addEventListener를 건 이벤트 이름들. 연결이 바뀌면 다시 걸어야 한다.
  const attachedNamesRef = useRef(new Set<string>());
  const sourceRef = useRef<EventSource | null>(null);

  const attachListener = useCallback((source: EventSource, eventName: string) => {
    if (attachedNamesRef.current.has(eventName)) return;
    attachedNamesRef.current.add(eventName);
    source.addEventListener(eventName, (event: MessageEvent) => {
      const handlers = handlersByEventRef.current.get(eventName);
      if (!handlers || handlers.size === 0) return;
      let payload: unknown = event.data;
      try {
        payload = JSON.parse(event.data);
      } catch {
        // data가 JSON이 아니면(예: 빈 문자열) 원본을 그대로 넘긴다.
      }
      handlers.forEach((handler) => handler(payload));
    });
  }, []);

  const subscribe = useCallback(
    (eventName: string, handler: Listener) => {
      let handlers = handlersByEventRef.current.get(eventName);
      if (!handlers) {
        handlers = new Set();
        handlersByEventRef.current.set(eventName, handlers);
      }
      handlers.add(handler);

      // 이미 열려 있는 연결에 새 이벤트 이름이 등록되면 그 자리에서 바로 걸어준다.
      if (sourceRef.current) {
        attachListener(sourceRef.current, eventName);
      }

      return () => {
        handlersByEventRef.current.get(eventName)?.delete(handler);
      };
    },
    [attachListener],
  );

  useEffect(() => {
    if (!isAuthenticated) return;

    const base = import.meta.env.VITE_API_BASE_URL ?? "";
    const source = new EventSource(`${base}/api/v1/sse/subscribe`, {
      withCredentials: true,
    });
    sourceRef.current = source;
    attachedNamesRef.current = new Set();

    source.addEventListener("connected", () => setConnected(true));
    source.onerror = () => setConnected(false);

    // 연결이 만들어지기 전에 이미 subscribe된 이벤트 이름들도 새 연결에 걸어준다.
    handlersByEventRef.current.forEach((_handlers, eventName) => attachListener(source, eventName));

    return () => {
      source.close();
      sourceRef.current = null;
      attachedNamesRef.current = new Set();
      setConnected(false);
    };
  }, [isAuthenticated, attachListener]);

  const value = useMemo<SseContextValue>(() => ({ connected, subscribe }), [connected, subscribe]);

  return <SseContext value={value}>{children}</SseContext>;
}

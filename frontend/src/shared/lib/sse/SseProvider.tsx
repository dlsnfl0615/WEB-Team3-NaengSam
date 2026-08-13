import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { useSessionStore } from "@/shared/store/sessionStore";
import { useMatchingStore } from "@/shared/store/matchingStore";
import { SseContext, type SseContextValue, type SseStatus } from "./SseContext";

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
 *
 * 연결 상태는 `SseStatus`로 세분화한다. `onerror`에서 `readyState`로 일시 장애(브라우저 자동 재연결,
 * `reconnecting`)와 영구 종료(예: 연결 한도 초과 204, `closed`)를 구분한다. `closed`이면 자동 재연결도
 * 무한 빠른 polling도 하지 않고, 사용자가 `reconnect()`로 직접 다시 시도한다.
 */
export function SseProvider({ children }: SseProviderProps) {
  const isAuthenticated = useSessionStore((state) => state.isAuthenticated);
  const [status, setStatus] = useState<SseStatus>("connecting");
  // reconnect()가 바뀔 때마다 연결 effect를 다시 실행해 EventSource를 새로 만든다.
  const [reconnectNonce, setReconnectNonce] = useState(0);

  // 로그인/로그아웃 전환 시 상태를 connecting으로 초기화한다(예: closed였다가 재로그인해도 배너가 남지 않도록).
  // effect가 아니라 렌더 중 조정 패턴을 쓴다 — https://react.dev/learn/you-might-not-need-an-effect
  const [wasAuthenticated, setWasAuthenticated] = useState(isAuthenticated);
  if (isAuthenticated !== wasAuthenticated) {
    setWasAuthenticated(isAuthenticated);
    setStatus("connecting");
  }

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

  const reconnect = useCallback(() => {
    // 수동 재연결: 상태를 connecting으로 되돌리고, effect 의존성 nonce를 올려 연결을 새로 맺게 한다.
    setStatus("connecting");
    setReconnectNonce((n) => n + 1);
  }, []);

  useEffect(() => {
    if (!isAuthenticated) return;

    const base = import.meta.env.VITE_API_BASE_URL ?? "";
    const source = new EventSource(`${base}/api/v1/sse/subscribe`, {
      withCredentials: true,
    });
    sourceRef.current = source;
    attachedNamesRef.current = new Set();

    source.addEventListener("connected", () => {
      setStatus("connected");
      // 연결(재연결 포함)이 서면 빠른 polling을 멈추고, 그동안 놓친 상태를 즉시 한 번 맞춘다.
      const matching = useMatchingStore.getState();
      matching.stopMatchingPolling();
      void matching.syncCurrentMatching();
      // 수행 중인 역할도 함께 맞춘다(역할 토글 잠금 판정 근거).
      void useSessionStore.getState().refreshUser();
    });
    source.onerror = () => {
      const matching = useMatchingStore.getState();
      if (source.readyState === EventSource.CLOSED) {
        // 영구 종료(예: 연결 한도 초과로 서버가 204를 내려 native EventSource가 재연결을 포기).
        // 무한 재연결·무한 빠른 polling을 하지 않고 사용자의 수동 재연결을 기다린다.
        setStatus("closed");
        source.close();
        matching.stopMatchingPolling();
      } else {
        // 일시 장애: 브라우저가 자동 재연결하는 동안 매칭 상태는 polling으로 복구한다.
        setStatus("reconnecting");
        matching.startMatchingPolling();
      }
    };

    // 연결이 만들어지기 전에 이미 subscribe된 이벤트 이름들도 새 연결에 걸어준다.
    handlersByEventRef.current.forEach((_handlers, eventName) => attachListener(source, eventName));

    return () => {
      source.close();
      sourceRef.current = null;
      attachedNamesRef.current = new Set();
      // 로그아웃·언마운트·재연결 재시작 시 남아 있는 polling timer를 반드시 제거한다.
      useMatchingStore.getState().stopMatchingPolling();
    };
  }, [isAuthenticated, attachListener, reconnectNonce]);

  const value = useMemo<SseContextValue>(
    () => ({ status, connected: status === "connected", subscribe, reconnect }),
    [status, subscribe, reconnect],
  );

  return <SseContext value={value}>{children}</SseContext>;
}

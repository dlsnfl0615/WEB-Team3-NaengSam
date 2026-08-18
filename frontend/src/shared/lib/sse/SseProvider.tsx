import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { api, emitUnauthorized } from "@/shared/api";
import { useSessionStore } from "@/shared/store/sessionStore";
import { useMatchingStore } from "@/shared/store/matchingStore";
import {
  SseTabCoordinator,
  type SseTabConnection,
  type SseTabConnectionCallbacks,
} from "./SseTabCoordinator";
import { SseContext, type SseContextValue, type SseStatus } from "./SseContext";

export interface SseProviderProps {
  children: ReactNode;
}

function disconnectUrl(connectionId: string): string {
  const base = import.meta.env.VITE_API_BASE_URL ?? "";
  return `${base}/api/v1/sse/disconnect?connectionId=${encodeURIComponent(connectionId)}`;
}

/** 명시적 종료(재연결·핸드오프·로그아웃·페이지 종료)에서만 쓴다. 실패는 무시한다 — heartbeat가 최종 정리한다. */
function sendDisconnectBeacon(connectionId: string): void {
  try {
    navigator.sendBeacon(disconnectUrl(connectionId));
  } catch {
    // best-effort
  }
}

interface ConnectedPayload {
  connectionId?: string;
}

function parseEventData(raw: string): unknown {
  try {
    return JSON.parse(raw);
  } catch {
    return raw;
  }
}

/**
 * {@link SseTabConnection}을 실제 `EventSource`로 구현한다. 대표 탭이 됐을 때만 만들어진다.
 * named event는 한 번 걸면 다시 떼지 않는다 — 구독이 사라져도 남는 리스너는 해당 이름의 핸들러 집합이
 * 비어 있으면 그냥 아무 일도 하지 않으므로 무해하다.
 *
 * 일시적 onerror(자동 재연결 대상)에서는 절대 disconnect beacon을 보내지 않는다 — 서버 emitter가
 * 아직 살아 있고, native EventSource의 자동 재연결을 그대로 신뢰하기 때문이다. beacon은 이 연결을
 * 명시적으로 내려놓을 때(`close()`, `reconnect()`)만 보낸다.
 */
function createEventSourceConnection({ onStatus, onEvent }: SseTabConnectionCallbacks): SseTabConnection {
  let source: EventSource | null = null;
  let attachedNames = new Set<string>();
  let currentEventNames: string[] = [];
  let currentConnectionId: string | null = null;

  function attachListener(eventName: string): void {
    if (!source || attachedNames.has(eventName)) return;
    attachedNames.add(eventName);
    source.addEventListener(eventName, (event: MessageEvent) => {
      onEvent(eventName, parseEventData(event.data));
    });
  }

  function open(): void {
    const base = import.meta.env.VITE_API_BASE_URL ?? "";
    source = new EventSource(`${base}/api/v1/sse/subscribe`, { withCredentials: true });
    attachedNames = new Set();

    source.addEventListener("connected", (event: MessageEvent) => {
      const payload = parseEventData(event.data) as ConnectedPayload;
      if (payload.connectionId) currentConnectionId = payload.connectionId;
      onStatus("connected");
    });
    source.onerror = () => {
      if (source?.readyState === EventSource.CLOSED) {
        // 영구 종료: 서버가 이미 이 emitter를 정리했다는 뜻이므로(예: 다른 곳에서 로그인해 세션이
        // 교체됨) beacon을 보낼 대상 자체가 없다. native EventSource도 더 재시도하지 않는다.
        onStatus("closed");
        source.close();
      } else {
        // 일시 장애: 자동 재연결을 그대로 둔다. 여기서 close/beacon을 하면 살아 있는 서버 emitter를
        // 스스로 끊어버리는 셈이라 절대 하지 않는다.
        onStatus("reconnecting");
      }
    };

    currentEventNames.forEach(attachListener);
  }

  function closeAndBeacon(): void {
    source?.close();
    source = null;
    if (currentConnectionId) {
      sendDisconnectBeacon(currentConnectionId);
      currentConnectionId = null;
    }
  }

  open();

  return {
    updateEventNames(eventNames) {
      currentEventNames = eventNames;
      eventNames.forEach(attachListener);
    },
    reconnect() {
      closeAndBeacon();
      onStatus("connecting");
      open();
    },
    close() {
      closeAndBeacon();
    },
  };
}

/**
 * 계정(userId) 하나에 Web Lock으로 뽑힌 대표 탭 하나만 실제 `EventSource`를 소유하게 하는 provider.
 * 화면(`useSse`)은 이 provider가 감싼 {@link SseTabCoordinator}에 이벤트 이름별 핸들러만 등록/해제한다.
 *
 * 연결 상태(`status`)는 대표 탭이든 follower든 동일하게 반영된다 — follower는 BroadcastChannel로 대표
 * 탭의 상태를 받는다. `connected` 전이에서 matching polling 중단·`syncCurrentMatching`·`refreshUser`를
 * 모두 실행하는 것도 대표/follower 공통이다(놓친 REST 상태를 두 경우 다 다시 맞춰야 하기 때문).
 *
 * `closed`(영구 종료)는 이제 사용자당 연결 상한이 사라졌으므로 사실상 "이 servlet 세션이 더 이상 계정의
 * 현재 ActiveSession이 아니다"(다른 곳에서 로그인)와 거의 동의어다. 대표 탭만 `/me`로 확인해, 세션이
 * 실제로 무효화됐으면(401) axios 인터셉터가 이 탭을 강제 로그아웃시키고, 같은 브라우저의 다른 탭에도
 * session-invalid를 브로드캐스트해 전부 정리되게 한다.
 */
export function SseProvider({ children }: SseProviderProps) {
  const isAuthenticated = useSessionStore((state) => state.isAuthenticated);
  const userId = useSessionStore((state) => state.user?.id);
  const [status, setStatus] = useState<SseStatus>("connecting");
  const coordinatorRef = useRef<SseTabCoordinator | null>(null);

  // 로그인/로그아웃 전환 시 상태를 connecting으로 초기화한다(예: closed였다가 재로그인해도 배너가 남지 않도록).
  // effect가 아니라 렌더 중 조정 패턴을 쓴다 — https://react.dev/learn/you-might-not-need-an-effect
  const [wasAuthenticated, setWasAuthenticated] = useState(isAuthenticated);
  if (isAuthenticated !== wasAuthenticated) {
    setWasAuthenticated(isAuthenticated);
    setStatus("connecting");
  }

  const subscribe = useCallback((eventName: string, handler: (data: unknown) => void) => {
    const coordinator = coordinatorRef.current;
    if (!coordinator) return () => {};
    return coordinator.subscribe(eventName, handler);
  }, []);

  const reconnect = useCallback(() => {
    coordinatorRef.current?.reconnect();
  }, []);

  useEffect(() => {
    if (!isAuthenticated || !userId) return;

    function handleStatusChange(nextStatus: SseStatus): void {
      setStatus(nextStatus);
      const matching = useMatchingStore.getState();
      if (nextStatus === "connected") {
        // 연결(재연결 포함)이 서면 빠른 polling을 멈추고, 그동안 놓친 상태를 즉시 한 번 맞춘다.
        matching.stopMatchingPolling();
        void matching.syncCurrentMatching();
        void useSessionStore.getState().refreshUser();
      } else if (nextStatus === "reconnecting") {
        // 일시 장애: 브라우저가 자동 재연결하는 동안 매칭 상태는 polling으로 복구한다.
        matching.startMatchingPolling();
      } else if (nextStatus === "closed") {
        matching.stopMatchingPolling();
        if (coordinatorRef.current?.isLeaderTab()) {
          // 세션 probe(별도 헤더 없음) — 실패하면 axios 인터셉터가 이 탭을 강제 로그아웃시킨다.
          api.me().catch(() => {
            coordinatorRef.current?.notifySessionInvalid();
          });
        }
      }
    }

    const coordinator = new SseTabCoordinator({
      userId,
      createConnection: createEventSourceConnection,
      onStatusChange: handleStatusChange,
      onSessionInvalid: () => emitUnauthorized(),
    });
    coordinatorRef.current = coordinator;
    coordinator.start();

    // 페이지 새로고침/종료: React 언마운트가 제때 돌지 않을 수 있으므로 별도로 정리한다.
    const handlePageHide = () => coordinator.stop();
    window.addEventListener("pagehide", handlePageHide);

    return () => {
      window.removeEventListener("pagehide", handlePageHide);
      coordinator.stop();
      coordinatorRef.current = null;
      // 로그아웃·언마운트·재연결 재시작 시 남아 있는 polling timer를 반드시 제거한다.
      useMatchingStore.getState().stopMatchingPolling();
    };
  }, [isAuthenticated, userId]);

  const value = useMemo<SseContextValue>(
    () => ({ status, connected: status === "connected", subscribe, reconnect }),
    [status, subscribe, reconnect],
  );

  return <SseContext value={value}>{children}</SseContext>;
}

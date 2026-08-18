import type { SseChannelMessage, SseTabStatus } from "./SseChannelMessage";

export type { SseTabStatus } from "./SseChannelMessage";

/** 실제 서버 연결(EventSource 등)을 열고 대표 탭이 구독해야 할 이벤트 이름을 알려줄 때 쓰는 최소 인터페이스. */
export interface SseTabConnection {
  /** 지금 이 연결에 걸어야 할 named event 전체 목록(로컬 구독 ∪ 원격 구독). 바뀔 때마다 다시 호출된다. */
  updateEventNames(eventNames: string[]): void;
  /** 사용자가 수동으로 재연결을 요청했다(로컬 또는 다른 탭의 reconnect-request). */
  reconnect(): void;
  /** 대표 탭 자리를 내려놓을 때(lock 해제, coordinator 종료) 호출된다. */
  close(): void;
}

export interface SseTabConnectionCallbacks {
  onStatus(status: SseTabStatus): void;
  onEvent(eventName: string, payload: unknown): void;
}

export type SseTabConnectionFactory = (callbacks: SseTabConnectionCallbacks) => SseTabConnection;

/** {@link BroadcastChannel}의 구조적 부분집합. 테스트에서 가짜 채널을 주입할 수 있도록 최소한만 요구한다. */
export interface SseBroadcastChannelLike {
  postMessage(message: SseChannelMessage): void;
  addEventListener(type: "message", listener: (event: { data: SseChannelMessage }) => void): void;
  close(): void;
}

/** {@link LockManager.request}의 구조적 부분집합. */
export interface SseLockManagerLike {
  request<T>(
    name: string,
    options: { mode: "exclusive"; signal: AbortSignal },
    callback: () => Promise<T> | T,
  ): Promise<T>;
}

export interface SseTabCoordinatorOptions {
  userId: string;
  createConnection: SseTabConnectionFactory;
  onStatusChange?: (status: SseTabStatus) => void;
  /**
   * 대표 탭이 세션 무효화를 확인해 {@link SseTabCoordinator#notifySessionInvalid}를 호출했거나, 다른
   * 탭이 보낸 session-invalid 메시지를 받았을 때 호출된다. 두 경우 모두를 같은 콜백으로 받는다 —
   * 어느 탭이든 전역 강제 로그아웃 흐름을 실행하면 되기 때문이다.
   */
  onSessionInvalid?: () => void;
  /** 테스트용 주입 지점. 기본값은 각각 `navigator.locks`/`new BroadcastChannel(name)`. */
  locks?: SseLockManagerLike;
  createChannel?: (name: string) => SseBroadcastChannelLike;
  /** 테스트에서 결정적인 peerId가 필요할 때만 넘긴다. 기본은 `crypto.randomUUID()`. */
  peerId?: string;
}

function hasNativeBroadcastChannel(): boolean {
  return typeof BroadcastChannel !== "undefined";
}

export function sseLockName(userId: string): string {
  return `naengsam:sse-lock:${userId}`;
}

export function sseChannelName(userId: string): string {
  return `naengsam:sse-channel:${userId}`;
}

function isAbortError(error: unknown): boolean {
  return error instanceof DOMException && error.name === "AbortError";
}

/**
 * 계정(userId) 하나에 대해 브라우저 탭 여러 개가 Web Lock으로 대표 탭 하나를 뽑고, 그 대표 탭만 실제
 * 서버 연결({@link SseTabConnection}, 보통 EventSource)을 소유하게 하는 조정자.
 *
 * - 각 문서 인스턴스는 메모리 UUID(`peerId`)로만 식별된다. 서버 세션과 무관하고 탭 복제로 값이 겹치지 않는다.
 * - Web Lock 요청은 `ifAvailable`로 포기하지 않고 대기시킨다 — 대표 탭이 종료되면(Lock 해제) 대기 중이던
 *   다음 탭이 자동으로 대표가 된다.
 * - 대표가 아닌 탭은 {@link BroadcastChannel}로 대표 탭의 이벤트/상태를 받고, 자신의 구독 이벤트 이름을
 *   대표 탭에 알려 대표 탭의 실제 연결이 그 이름들도 구독하게 한다.
 *
 * Web Locks API 또는 BroadcastChannel이 없는 환경에서는(오래된 브라우저) 탭마다 별도 연결을 만들어
 * 서로 밀어내는 이전 방식으로 되돌아가지 않는다 — 즉시 `closed`로 degrade하고 서버 연결을 만들지 않는다.
 */
export class SseTabCoordinator {
  readonly peerId: string;

  private readonly userId: string;
  private readonly createConnectionFn: SseTabConnectionFactory;
  private readonly onStatusChangeCallback: (status: SseTabStatus) => void;
  private readonly onSessionInvalidCallback: () => void;
  private readonly locks: SseLockManagerLike | undefined;
  private readonly createChannelFn: (name: string) => SseBroadcastChannelLike;
  private readonly hasChannelSupport: boolean;

  private channel: SseBroadcastChannelLike | undefined;
  private abortController: AbortController | undefined;
  private releaseLock: (() => void) | undefined;
  private connection: SseTabConnection | null = null;

  private started = false;
  private stopped = false;
  private leader = false;
  private status: SseTabStatus = "connecting";

  private readonly handlersByEvent = new Map<string, Set<(payload: unknown) => void>>();
  /** 대표 탭일 때만 의미 있다: 다른 탭의 peerId → 그 탭이 구독 중인 이벤트 이름들. */
  private readonly remoteSubscriptionsByPeer = new Map<string, Set<string>>();
  private currentLeaderId: string | null = null;
  private currentEpoch = 0;
  private myEpoch = 0;

  constructor(options: SseTabCoordinatorOptions) {
    this.peerId = options.peerId ?? crypto.randomUUID();
    this.userId = options.userId;
    this.createConnectionFn = options.createConnection;
    this.onStatusChangeCallback = options.onStatusChange ?? (() => {});
    this.onSessionInvalidCallback = options.onSessionInvalid ?? (() => {});
    this.locks =
      options.locks ??
      (typeof navigator !== "undefined" && navigator.locks
        ? (navigator.locks as unknown as SseLockManagerLike)
        : undefined);
    this.hasChannelSupport = options.createChannel != null || hasNativeBroadcastChannel();
    this.createChannelFn =
      options.createChannel ?? ((name) => new BroadcastChannel(name) as unknown as SseBroadcastChannelLike);
  }

  getStatus(): SseTabStatus {
    return this.status;
  }

  isLeaderTab(): boolean {
    return this.leader;
  }

  /** Web Lock 획득 시도를 시작하고 BroadcastChannel을 연다. 두 번째 호출부터는 no-op이다. */
  start(): void {
    if (this.started) return;
    this.started = true;

    if (!this.isSupported()) {
      this.updateStatus("closed");
      return;
    }

    this.channel = this.createChannelFn(sseChannelName(this.userId));
    this.channel.addEventListener("message", (event) => this.handleMessage(event.data));
    this.requestLock();
  }

  /**
   * 대기 중이면 lock 요청을 취소하고, 대표 탭이었다면 연결을 닫은 뒤 lock을 반환한다. 이후 다시 시작하지
   * 않는다(새 lifecycle에는 새 {@link SseTabCoordinator} 인스턴스를 만든다).
   */
  stop(): void {
    if (!this.started || this.stopped) {
      this.stopped = true;
      return;
    }
    this.stopped = true;

    this.broadcast({ type: "peer-closing", peerId: this.peerId });

    if (this.leader) {
      // lock을 실제로 반환하기 전에 정리를 마친다 — release는 대기 중이던 다음 탭의 콜백을 동기적으로
      // 실행시킬 수 있으므로, 그때 이 탭은 이미 leader가 아닌 상태여야 한다.
      this.connection?.close();
      this.connection = null;
      this.leader = false;
      this.remoteSubscriptionsByPeer.clear();
      this.releaseLock?.();
      this.releaseLock = undefined;
    } else {
      this.abortController?.abort();
    }

    this.channel?.close();
  }

  /** 이벤트 이름 하나에 핸들러를 등록한다. 반환된 함수를 호출하면 등록을 해제한다. */
  subscribe(eventName: string, handler: (payload: unknown) => void): () => void {
    let handlers = this.handlersByEvent.get(eventName);
    if (!handlers) {
      handlers = new Set();
      this.handlersByEvent.set(eventName, handlers);
    }
    handlers.add(handler);
    this.onLocalSubscriptionsChanged();

    return () => {
      const current = this.handlersByEvent.get(eventName);
      if (!current) return;
      current.delete(handler);
      if (current.size === 0) this.handlersByEvent.delete(eventName);
      this.onLocalSubscriptionsChanged();
    };
  }

  /**
   * 서버 세션이 더 이상 유효하지 않음을 확인했을 때(예: `/me` 프로브 실패) 호출한다. 이 탭 자신은 이미
   * axios 인터셉터가 별도로 강제 로그아웃을 처리하므로, 이 메서드는 같은 브라우저의 다른 탭들에게만
   * 알리면 된다.
   */
  notifySessionInvalid(): void {
    this.broadcast({ type: "session-invalid", senderId: this.peerId });
  }

  /** 대표 탭이면 연결에 직접 재연결을 요청하고, 아니면 대표 탭에 재연결 요청을 전달한다. */
  reconnect(): void {
    if (this.leader) {
      this.connection?.reconnect();
      return;
    }
    this.broadcast({ type: "reconnect-request", senderId: this.peerId });
  }

  private isSupported(): boolean {
    return this.locks != null && this.hasChannelSupport;
  }

  private requestLock(): void {
    this.abortController = new AbortController();
    this.locks!.request(
      sseLockName(this.userId),
      { mode: "exclusive", signal: this.abortController.signal },
      () => this.runAsLeader(),
    ).catch((error: unknown) => {
      if (isAbortError(error)) return;
      this.updateStatus("closed");
    });
  }

  /**
   * Web Lock 콜백 본체. 반환하는 Promise가 풀려야 Lock Manager가 lock을 반환하고 다음 대기자를 부르므로,
   * 대표 자리를 내려놓는 정리(연결 종료, leader 플래그 해제)는 이 Promise가 아니라 {@link #stop}이
   * `releaseLock`을 호출하기 *직전에* 동기적으로 끝내둔다 — 그래야 다음 탭이 leader가 되는 시점에 이
   * 탭은 이미 확실히 leader가 아니다.
   */
  private runAsLeader(): Promise<void> {
    this.leader = true;
    this.myEpoch = Math.max(this.currentEpoch, this.myEpoch) + 1;
    this.currentEpoch = this.myEpoch;
    this.currentLeaderId = this.peerId;
    this.remoteSubscriptionsByPeer.clear();

    this.connection = this.createConnectionFn({
      onStatus: (status) => this.handleLeaderStatus(status),
      onEvent: (eventName, payload) => this.handleLeaderEvent(eventName, payload),
    });
    this.connection.updateEventNames(this.allSubscribedEventNames());
    this.broadcast({ type: "leader-ready", senderId: this.peerId, epoch: this.myEpoch });

    return new Promise<void>((resolve) => {
      this.releaseLock = resolve;
    });
  }

  private handleLeaderStatus(status: SseTabStatus): void {
    this.updateStatus(status);
    this.broadcast({ type: "status", senderId: this.peerId, status });
  }

  private handleLeaderEvent(eventName: string, payload: unknown): void {
    // BroadcastChannel은 발신 탭 자신에게 메시지를 돌려주지 않으므로, 대표 탭 자신의 로컬 핸들러에는
    // 직접 전달해야 한다.
    this.dispatchLocal(eventName, payload);
    this.broadcast({ type: "event", senderId: this.peerId, eventName, payload });
  }

  private onLocalSubscriptionsChanged(): void {
    if (this.leader) {
      this.connection?.updateEventNames(this.allSubscribedEventNames());
      return;
    }
    this.broadcastLocalSubscriptions();
  }

  private broadcastLocalSubscriptions(): void {
    this.broadcast({
      type: "subscriptions",
      peerId: this.peerId,
      eventNames: Array.from(this.handlersByEvent.keys()),
    });
  }

  private allSubscribedEventNames(): string[] {
    const names = new Set(this.handlersByEvent.keys());
    this.remoteSubscriptionsByPeer.forEach((eventNames) => {
      eventNames.forEach((name) => names.add(name));
    });
    return Array.from(names);
  }

  private dispatchLocal(eventName: string, payload: unknown): void {
    this.handlersByEvent.get(eventName)?.forEach((handler) => handler(payload));
  }

  private handleMessage(message: SseChannelMessage): void {
    switch (message.type) {
      case "leader-ready":
        this.handleLeaderReady(message.senderId, message.epoch);
        return;
      case "subscriptions":
        this.handleRemoteSubscriptions(message.peerId, message.eventNames);
        return;
      case "event":
        this.handleRemoteEvent(message.senderId, message.eventName, message.payload);
        return;
      case "status":
        this.handleRemoteStatus(message.senderId, message.status);
        return;
      case "reconnect-request":
        if (this.leader) this.connection?.reconnect();
        return;
      case "peer-closing":
        this.handlePeerClosing(message.peerId);
        return;
      case "session-invalid":
        this.onSessionInvalidCallback();
        return;
    }
  }

  private handleLeaderReady(senderId: string, epoch: number): void {
    // 오래된(stale) leader-ready가 재정렬돼 뒤늦게 도착해도 현재 대표를 덮어쓰지 않는다.
    if (epoch < this.currentEpoch) return;
    this.currentEpoch = epoch;
    this.currentLeaderId = senderId;
    if (this.leader) return;
    // 새 대표가 등장했으니, 대표가 모르는 내 구독 목록을 다시 알려준다.
    this.broadcastLocalSubscriptions();
  }

  private handleRemoteSubscriptions(peerId: string, eventNames: string[]): void {
    if (!this.leader) return;
    if (eventNames.length === 0) {
      this.remoteSubscriptionsByPeer.delete(peerId);
    } else {
      this.remoteSubscriptionsByPeer.set(peerId, new Set(eventNames));
    }
    this.connection?.updateEventNames(this.allSubscribedEventNames());
  }

  private handleRemoteEvent(senderId: string, eventName: string, payload: unknown): void {
    if (this.leader) return;
    if (!this.acceptFromSender(senderId)) return;
    this.dispatchLocal(eventName, payload);
  }

  private handleRemoteStatus(senderId: string, status: SseTabStatus): void {
    if (this.leader) return;
    if (!this.acceptFromSender(senderId)) return;
    this.updateStatus(status);
  }

  /**
   * status/event 발신자가 현재 대표와 같은지 확인한다. `leader-ready`를 아직 못 받은(예: 대표가 이미
   * 정해진 뒤 늦게 뜬 탭) 상태에서는 처음 도착한 발신자를 잠정 대표로 채택한다 — 이후 `leader-ready`가
   * epoch와 함께 도착하면 그 값이 우선한다.
   */
  private acceptFromSender(senderId: string): boolean {
    if (this.currentLeaderId === null) {
      this.currentLeaderId = senderId;
      return true;
    }
    return senderId === this.currentLeaderId;
  }

  private handlePeerClosing(peerId: string): void {
    if (!this.leader) return;
    this.remoteSubscriptionsByPeer.delete(peerId);
    this.connection?.updateEventNames(this.allSubscribedEventNames());
  }

  private updateStatus(status: SseTabStatus): void {
    if (this.status === status) return;
    this.status = status;
    this.onStatusChangeCallback(status);
  }

  private broadcast(message: SseChannelMessage): void {
    this.channel?.postMessage(message);
  }
}

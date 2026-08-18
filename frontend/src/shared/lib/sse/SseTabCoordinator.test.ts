import { describe, expect, it, vi } from "vitest";
import type { SseChannelMessage } from "./SseChannelMessage";
import {
  SseTabCoordinator,
  sseChannelName,
  type SseBroadcastChannelLike,
  type SseLockManagerLike,
  type SseTabConnection,
  type SseTabConnectionCallbacks,
} from "./SseTabCoordinator";

/** 실제 Web Locks API처럼 exclusive lock 하나를 FIFO 대기열로 중재하는 가짜 LockManager. */
class FakeLockManager implements SseLockManagerLike {
  private locked = false;
  private queue: Array<() => void> = [];

  request<T>(
    _name: string,
    options: { mode: "exclusive"; signal: AbortSignal },
    callback: () => Promise<T> | T,
  ): Promise<T> {
    return new Promise<T>((resolve, reject) => {
      const acquire = (): boolean => {
        if (this.locked) return false;
        this.locked = true;
        Promise.resolve(callback()).then(
          (value) => {
            this.locked = false;
            resolve(value);
            this.runNext();
          },
          (error: unknown) => {
            this.locked = false;
            reject(error);
            this.runNext();
          },
        );
        return true;
      };

      if (options.signal.aborted) {
        reject(new DOMException("aborted", "AbortError"));
        return;
      }
      if (acquire()) return;

      const waiter = () => acquire();
      options.signal.addEventListener(
        "abort",
        () => {
          const index = this.queue.indexOf(waiter);
          if (index >= 0) this.queue.splice(index, 1);
          reject(new DOMException("aborted", "AbortError"));
        },
        { once: true },
      );
      this.queue.push(waiter);
    });
  }

  private runNext(): void {
    this.queue.shift()?.();
  }
}

/** 같은 이름의 채널 인스턴스끼리만 메시지를 주고받는 가짜 BroadcastChannel 허브. */
class FakeBroadcastChannelHub {
  private readonly channelsByName = new Map<string, Set<FakeBroadcastChannel>>();

  register(channel: FakeBroadcastChannel): void {
    let peers = this.channelsByName.get(channel.name);
    if (!peers) {
      peers = new Set();
      this.channelsByName.set(channel.name, peers);
    }
    peers.add(channel);
  }

  unregister(channel: FakeBroadcastChannel): void {
    this.channelsByName.get(channel.name)?.delete(channel);
  }

  broadcast(sender: FakeBroadcastChannel, message: SseChannelMessage): void {
    // BroadcastChannel은 발신 채널 자신에게는 메시지를 돌려주지 않는다.
    this.channelsByName.get(sender.name)?.forEach((channel) => {
      if (channel !== sender) channel.deliver(message);
    });
  }

  /** 특정 발신자 없이 채널 이름의 모든 구독자에게 메시지를 즉시 전달한다(늦게 도착한 메시지 재현용). */
  deliverToAll(name: string, message: SseChannelMessage): void {
    this.channelsByName.get(name)?.forEach((channel) => channel.deliver(message));
  }
}

class FakeBroadcastChannel implements SseBroadcastChannelLike {
  readonly name: string;
  private readonly hub: FakeBroadcastChannelHub;
  private readonly listeners: Array<(event: { data: SseChannelMessage }) => void> = [];

  constructor(name: string, hub: FakeBroadcastChannelHub) {
    this.name = name;
    this.hub = hub;
    this.hub.register(this);
  }

  postMessage(message: SseChannelMessage): void {
    this.hub.broadcast(this, message);
  }

  addEventListener(_type: "message", listener: (event: { data: SseChannelMessage }) => void): void {
    this.listeners.push(listener);
  }

  close(): void {
    this.hub.unregister(this);
  }

  deliver(message: SseChannelMessage): void {
    this.listeners.forEach((listener) => listener({ data: message }));
  }
}

class FakeConnection implements SseTabConnection {
  readonly callbacks: SseTabConnectionCallbacks;
  eventNames: string[] = [];
  closed = false;
  reconnectCount = 0;

  constructor(callbacks: SseTabConnectionCallbacks) {
    this.callbacks = callbacks;
  }

  updateEventNames(eventNames: string[]): void {
    this.eventNames = [...eventNames].sort();
  }

  reconnect(): void {
    this.reconnectCount += 1;
  }

  close(): void {
    this.closed = true;
  }
}

const USER_ID = "user-1";

/**
 * lock 반환(release)은 `LockManager.request`가 돌려준 Promise의 `.then()` 체인을 통해 다음 대기자를
 * 깨우므로, 실제로는 한 틱 늦게 일어난다. `stop()` 직후 승계 결과를 확인하려면 마이크로태스크 큐를
 * 흘려보내야 한다.
 */
async function flushMicrotasks(): Promise<void> {
  await Promise.resolve();
  await Promise.resolve();
}

function setupHarness() {
  const lockManager = new FakeLockManager();
  const channelHub = new FakeBroadcastChannelHub();
  const connections: FakeConnection[] = [];

  function createTab(peerId: string, onStatusChange = vi.fn()) {
    const coordinator = new SseTabCoordinator({
      userId: USER_ID,
      peerId,
      locks: lockManager,
      createChannel: (name) => new FakeBroadcastChannel(name, channelHub),
      createConnection: (callbacks) => {
        const connection = new FakeConnection(callbacks);
        connections.push(connection);
        return connection;
      },
      onStatusChange,
    });
    return { coordinator, onStatusChange };
  }

  return { lockManager, channelHub, connections, createTab };
}

describe("SseTabCoordinator", () => {
  it("탭 2개가 동시에 시작해도 leader는 하나다", () => {
    const { createTab } = setupHarness();
    const tabA = createTab("peer-a");
    const tabB = createTab("peer-b");

    tabA.coordinator.start();
    tabB.coordinator.start();

    const leaders = [tabA.coordinator, tabB.coordinator].filter((c) => c.isLeaderTab());
    expect(leaders).toHaveLength(1);
  });

  it("follower는 서버 연결(EventSource 소유권)을 얻지 않는다", () => {
    const { createTab, connections } = setupHarness();
    const tabA = createTab("peer-a");
    const tabB = createTab("peer-b");

    tabA.coordinator.start();
    tabB.coordinator.start();

    expect(connections).toHaveLength(1);
  });

  it("leader가 종료되면 대기하던 follower가 lock을 승계해 새 leader가 된다", async () => {
    const { createTab, connections } = setupHarness();
    const tabA = createTab("peer-a");
    const tabB = createTab("peer-b");
    tabA.coordinator.start();
    tabB.coordinator.start();
    expect(tabA.coordinator.isLeaderTab()).toBe(true);
    expect(tabB.coordinator.isLeaderTab()).toBe(false);

    tabA.coordinator.stop();
    await flushMicrotasks();

    expect(tabB.coordinator.isLeaderTab()).toBe(true);
    expect(connections).toHaveLength(2);
    expect(connections[0]!.closed).toBe(true);
  });

  it("follower의 구독 목록이 leader의 연결에 전달된다", () => {
    const { createTab, connections } = setupHarness();
    const leader = createTab("peer-leader");
    const follower = createTab("peer-follower");
    leader.coordinator.start();
    follower.coordinator.start();

    follower.coordinator.subscribe("offer_popup", vi.fn());

    expect(connections[0]!.eventNames).toEqual(["offer_popup"]);
  });

  it("leader-ready 수신 후 follower가 구독 스냅샷을 다시 보낸다", async () => {
    const { createTab, connections } = setupHarness();
    const firstLeader = createTab("peer-first-leader");
    const nextLeader = createTab("peer-next-leader");
    const follower = createTab("peer-follower");
    firstLeader.coordinator.start(); // 대표 자리를 선점 — 이 대표가 낸 leader-ready는 아직 follower가 못 듣는다.

    // follower는 구독을 채널이 열리기도 전에 등록한다 — 이때의 subscriptions 브로드캐스트는 유실된다.
    follower.coordinator.subscribe("delivery_location", vi.fn());
    // 대기열 순서를 nextLeader보다 앞에 두지 않도록, follower보다 먼저 큐에 넣어 둔다.
    nextLeader.coordinator.start();
    follower.coordinator.start();

    firstLeader.coordinator.stop(); // nextLeader가 승계하며 leader-ready(epoch 2)를 새로 브로드캐스트한다.
    await flushMicrotasks();

    expect(nextLeader.coordinator.isLeaderTab()).toBe(true);
    expect(connections[1]!.eventNames).toEqual(["delivery_location"]);
  });

  it("이미 연결된 leader 뒤에 참가한 follower도 현재 상태를 즉시 받는다", () => {
    const { createTab, connections } = setupHarness();
    const leader = createTab("peer-leader");
    leader.coordinator.start();
    connections[0]!.callbacks.onStatus("connected");

    const follower = createTab("peer-late-follower");
    follower.coordinator.start();

    expect(follower.coordinator.getStatus()).toBe("connected");
  });

  it("leader 연결 후 늦게 참가한 follower의 기존 구독도 leader 연결에 반영된다", () => {
    const { createTab, connections } = setupHarness();
    const leader = createTab("peer-leader");
    leader.coordinator.start();

    const follower = createTab("peer-late-follower");
    follower.coordinator.subscribe("delivery_ping", vi.fn());
    follower.coordinator.start();

    expect(connections[0]!.eventNames).toContain("delivery_ping");
  });

  it("leader가 받은 서버 이벤트가 follower에게 정확히 한 번 전달된다", () => {
    const { createTab, connections } = setupHarness();
    const leader = createTab("peer-leader");
    const follower = createTab("peer-follower");
    leader.coordinator.start();
    follower.coordinator.start();
    const handler = vi.fn();
    follower.coordinator.subscribe("offer_popup", handler);

    connections[0]!.callbacks.onEvent("offer_popup", { orderId: "o-1" });

    expect(handler).toHaveBeenCalledTimes(1);
    expect(handler).toHaveBeenCalledWith({ orderId: "o-1" });
  });

  it("leader 자신의 로컬 핸들러에도 서버 이벤트가 전달된다", () => {
    const { createTab, connections } = setupHarness();
    const leader = createTab("peer-leader");
    leader.coordinator.start();
    const handler = vi.fn();
    leader.coordinator.subscribe("offer_popup", handler);

    connections[0]!.callbacks.onEvent("offer_popup", { orderId: "o-1" });

    expect(handler).toHaveBeenCalledTimes(1);
  });

  it("peer-closing을 받으면 leader가 해당 탭의 원격 구독을 정리한다", () => {
    const { createTab, connections } = setupHarness();
    const leader = createTab("peer-leader");
    const follower = createTab("peer-follower");
    leader.coordinator.start();
    follower.coordinator.start();
    follower.coordinator.subscribe("offer_popup", vi.fn());
    expect(connections[0]!.eventNames).toEqual(["offer_popup"]);

    follower.coordinator.stop();

    expect(connections[0]!.eventNames).toEqual([]);
  });

  it("로그아웃(stop) 시 대기 중이던 lock 요청을 취소하고 leader가 되지 않는다", () => {
    const { createTab, connections } = setupHarness();
    const leader = createTab("peer-leader");
    const waiting = createTab("peer-waiting");
    leader.coordinator.start();
    waiting.coordinator.start();
    expect(waiting.coordinator.isLeaderTab()).toBe(false);

    waiting.coordinator.stop();
    leader.coordinator.stop();

    expect(waiting.coordinator.isLeaderTab()).toBe(false);
    expect(connections).toHaveLength(1);
  });

  it("StrictMode의 mount/unmount/mount 반복 후에도 owner는 하나다", async () => {
    const { createTab, connections } = setupHarness();
    const strictModeFirst = createTab("peer-strict-1");
    strictModeFirst.coordinator.start();
    strictModeFirst.coordinator.stop();

    const strictModeSecond = createTab("peer-strict-2");
    strictModeSecond.coordinator.start();
    await flushMicrotasks();

    expect(strictModeFirst.coordinator.isLeaderTab()).toBe(false);
    expect(strictModeSecond.coordinator.isLeaderTab()).toBe(true);
    // StrictMode의 첫 mount가 만들었다 곧바로 닫은 연결 1개 + 최종적으로 살아남은 연결 1개.
    expect(connections).toHaveLength(2);
    expect(connections.filter((connection) => !connection.closed)).toHaveLength(1);
  });

  it("이전 leader의 뒤늦은 상태 메시지가 새 leader의 상태를 덮어쓰지 않는다", async () => {
    const { createTab, connections, channelHub } = setupHarness();
    const firstLeader = createTab("peer-first");
    const secondLeader = createTab("peer-second");
    const follower = createTab("peer-follower");
    firstLeader.coordinator.start();
    secondLeader.coordinator.start();
    follower.coordinator.start();

    connections[0]!.callbacks.onStatus("connected");
    expect(follower.coordinator.getStatus()).toBe("connected");

    firstLeader.coordinator.stop(); // secondLeader가 새 leader로 승계, leader-ready(epoch 2) 브로드캐스트
    await flushMicrotasks();
    connections[1]!.callbacks.onStatus("connecting");
    expect(follower.coordinator.getStatus()).toBe("connecting");

    // 이미 죽은 첫 leader가 재정렬로 뒤늦게 보낸 것처럼 재현한 stale 메시지: 새 leader가 아니므로 무시된다.
    channelHub.deliverToAll(sseChannelName(USER_ID), {
      type: "status",
      senderId: "peer-first",
      status: "closed",
    });

    expect(follower.coordinator.getStatus()).toBe("connecting");
  });
});

import { act, StrictMode, use } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import { api, emitUnauthorized } from "@/shared/api";
import { useSessionStore } from "@/shared/store/sessionStore";
import { useMatchingStore } from "@/shared/store/matchingStore";
import { SseProvider } from "./SseProvider";
import { SseStatusBanner } from "./SseStatusBanner";
import { useSse } from "./useSse";
import { SseContext, type SseContextValue, type SseHandlers } from "./SseContext";
import type { AuthUser } from "@/shared/mock/types";

vi.mock("@/shared/api", () => ({
  api: {
    me: vi.fn(),
  },
  emitUnauthorized: vi.fn(),
  SESSION_PROBE_HEADER: "X-Session-Probe",
  isApiError: () => false,
}));

/** 실제 EventSource 대신 테스트에서 직접 이벤트를 흘려보낼 수 있는 가짜 구현. */
class FakeEventSource {
  static CONNECTING = 0;
  static OPEN = 1;
  static CLOSED = 2;
  static instances: FakeEventSource[] = [];
  listenersByType = new Map<string, Set<(event: MessageEvent) => void>>();
  closed = false;
  readyState = FakeEventSource.CONNECTING;
  onerror: (() => void) | null = null;
  url: string;

  constructor(url: string) {
    this.url = url;
    FakeEventSource.instances.push(this);
  }

  addEventListener(type: string, listener: (event: MessageEvent) => void) {
    if (!this.listenersByType.has(type)) this.listenersByType.set(type, new Set());
    this.listenersByType.get(type)!.add(listener);
  }

  removeEventListener(type: string, listener: (event: MessageEvent) => void) {
    this.listenersByType.get(type)?.delete(listener);
  }

  close() {
    this.closed = true;
    this.readyState = FakeEventSource.CLOSED;
  }

  /** onerror 발생 시 브라우저가 세팅하는 readyState를 재현한다. */
  fail(readyState: number) {
    this.readyState = readyState;
    this.onerror?.();
  }

  dispatch(type: string, data: unknown) {
    const event = { data: JSON.stringify(data) } as MessageEvent;
    this.listenersByType.get(type)?.forEach((listener) => listener(event));
  }
}

/** 같은 이름끼리만 통신하는 전역 가짜 BroadcastChannel. 실제 브라우저 탭 간 채널과 동일하게 동작한다. */
class FakeBroadcastChannel {
  static hub = new Map<string, Set<FakeBroadcastChannel>>();
  name: string;
  private listeners: Array<(event: { data: unknown }) => void> = [];

  constructor(name: string) {
    this.name = name;
    let peers = FakeBroadcastChannel.hub.get(name);
    if (!peers) {
      peers = new Set();
      FakeBroadcastChannel.hub.set(name, peers);
    }
    peers.add(this);
  }

  postMessage(message: unknown) {
    FakeBroadcastChannel.hub.get(this.name)?.forEach((peer) => {
      if (peer !== this) peer.deliver(message);
    });
  }

  addEventListener(_type: string, listener: (event: { data: unknown }) => void) {
    this.listeners.push(listener);
  }

  close() {
    FakeBroadcastChannel.hub.get(this.name)?.delete(this);
  }

  deliver(message: unknown) {
    this.listeners.forEach((listener) => listener({ data: message }));
  }
}

/** 실제 Web Locks API처럼 exclusive lock 하나를 FIFO 대기열로 중재하는 가짜 LockManager. */
class FakeLockManager {
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

function Consumer({ handlers, enabled }: { handlers: SseHandlers; enabled?: boolean }) {
  useSse(handlers, { enabled });
  return null;
}

/** 탭(provider) 하나의 SSE 컨텍스트를 관찰하는 프로브. 여러 탭을 동시에 렌더링할 때 탭별로 만든다. */
function createProbe() {
  let ctx: SseContextValue | null = null;
  function Probe() {
    ctx = use(SseContext);
    return null;
  }
  return { Probe, current: () => ctx };
}

/** lock 승계는 Promise 체인을 거치므로, 승계 결과를 확인하기 전에 마이크로태스크를 흘려보내야 한다. */
async function flush(): Promise<void> {
  await act(async () => {
    await Promise.resolve();
    await Promise.resolve();
  });
}

const TEST_USER: AuthUser = { id: "user-1", name: "테스트", roles: ["부르미"], boormiRating: 0, email: "t@t.com" };

beforeEach(() => {
  FakeEventSource.instances = [];
  FakeBroadcastChannel.hub = new Map();
  vi.stubGlobal("EventSource", FakeEventSource);
  vi.stubGlobal("BroadcastChannel", FakeBroadcastChannel);
  Object.defineProperty(navigator, "locks", {
    value: new FakeLockManager(),
    configurable: true,
  });
  Object.defineProperty(navigator, "sendBeacon", {
    value: vi.fn().mockReturnValue(true),
    configurable: true,
    writable: true,
  });
  useSessionStore.setState({ isAuthenticated: true, hydrated: true, user: TEST_USER });
  // 세션 probe 기본값: 대부분의 테스트는 closed 경로를 직접 검증하지 않으므로, 우연히 probe가 불려도
  // 강제 로그아웃으로 이어지지 않도록 성공 응답을 기본으로 둔다. 무효화를 검증하는 테스트는 개별적으로
  // mockRejectedValueOnce로 덮어쓴다.
  // boormiId를 반드시 채운다 — "connected" 상태 전이가 부르는 refreshUser()가 이 응답으로
  // useSessionStore의 user를 덮어쓰므로, 비워두면 userId가 사라져 SseProvider effect가 꺼진다.
  vi.mocked(api.me).mockReset().mockResolvedValue({ result: { boormiId: TEST_USER.id } } as never);
  vi.mocked(emitUnauthorized).mockReset();
  // 매칭 polling은 별도로 검증하므로 여기서는 호출 여부만 스파이한다(실제 api 조회를 막는다).
  useMatchingStore.setState({
    startMatchingPolling: vi.fn(),
    stopMatchingPolling: vi.fn(),
    syncCurrentMatching: vi.fn().mockResolvedValue(undefined),
  });
});

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  Reflect.deleteProperty(navigator, "locks");
  Reflect.deleteProperty(navigator, "sendBeacon");
});

describe("SseProvider", () => {
  it("한 탭에서 여러 useSse가 있어도 EventSource는 하나만 만든다", () => {
    render(
      <SseProvider>
        <Consumer handlers={{ event_a: vi.fn() }} />
        <Consumer handlers={{ event_b: vi.fn() }} />
      </SseProvider>,
    );

    expect(FakeEventSource.instances).toHaveLength(1);
  });

  it("여러 탭 중 leader만 EventSource를 만든다", () => {
    render(<SseProvider><Consumer handlers={{}} /></SseProvider>);
    render(<SseProvider><Consumer handlers={{}} /></SseProvider>);

    expect(FakeEventSource.instances).toHaveLength(1);
  });

  it("handler별로 등록한 이벤트만 전달받는다", () => {
    const onA = vi.fn();
    const onB = vi.fn();
    render(
      <SseProvider>
        <Consumer handlers={{ event_a: onA }} />
        <Consumer handlers={{ event_b: onB }} />
      </SseProvider>,
    );
    const source = FakeEventSource.instances[0];

    act(() => {
      source.dispatch("event_a", { value: 1 });
    });

    expect(onA).toHaveBeenCalledWith({ value: 1 });
    expect(onB).not.toHaveBeenCalled();
  });

  it("follower의 handler가 leader의 서버 이벤트를 수신한다", () => {
    const onFollower = vi.fn();
    render(<SseProvider><Consumer handlers={{}} /></SseProvider>); // leader
    render(<SseProvider><Consumer handlers={{ delivery_location: onFollower }} /></SseProvider>); // follower
    const leaderSource = FakeEventSource.instances[0];

    act(() => {
      leaderSource.dispatch("delivery_location", { lat: 37.5 });
    });

    expect(onFollower).toHaveBeenCalledWith({ lat: 37.5 });
  });

  it("follower의 구독 이름이 leader EventSource에 등록된다", () => {
    render(<SseProvider><Consumer handlers={{}} /></SseProvider>); // leader
    render(<SseProvider><Consumer handlers={{ offer_popup: vi.fn() }} /></SseProvider>); // follower

    expect(FakeEventSource.instances[0].listenersByType.has("offer_popup")).toBe(true);
  });

  it("화면을 벗어나면 handler만 제거되고 EventSource는 유지된다", () => {
    const onA = vi.fn();
    function Wrapper({ mounted }: { mounted: boolean }) {
      return <SseProvider>{mounted && <Consumer handlers={{ event_a: onA }} />}</SseProvider>;
    }

    const { rerender } = render(<Wrapper mounted />);
    const source = FakeEventSource.instances[0];

    rerender(<Wrapper mounted={false} />);

    expect(source.closed).toBe(false);
    act(() => {
      source.dispatch("event_a", {});
    });
    expect(onA).not.toHaveBeenCalled();
  });

  it("일반 탭의 unmount는 leader EventSource에 영향을 주지 않는다", () => {
    render(<SseProvider><Consumer handlers={{}} /></SseProvider>); // leader
    const follower = render(<SseProvider><Consumer handlers={{}} /></SseProvider>);
    const leaderSource = FakeEventSource.instances[0];

    follower.unmount();

    expect(leaderSource.closed).toBe(false);
  });

  it("leader가 unmount되면 follower가 lock을 승계해 새 연결을 만든다", async () => {
    const leader = render(<SseProvider><Consumer handlers={{}} /></SseProvider>);
    render(<SseProvider><Consumer handlers={{}} /></SseProvider>); // follower
    const leaderSource = FakeEventSource.instances[0];

    act(() => {
      leader.unmount();
    });
    await flush();

    expect(leaderSource.closed).toBe(true);
    expect(FakeEventSource.instances).toHaveLength(2);
    expect(FakeEventSource.instances[1].closed).toBe(false);
  });

  it("leader 새로고침 시 이전 EventSource를 닫고 disconnect beacon을 보낸다", () => {
    const firstTab = render(<SseProvider><Consumer handlers={{}} /></SseProvider>);
    const firstSource = FakeEventSource.instances[0];
    act(() => {
      firstSource.dispatch("connected", { connectionId: "conn-1" });
    });

    // 새로고침 재현: 이전 문서를 내려놓고(peerId도 사라짐) 새 문서를 새로 마운트한다.
    act(() => {
      firstTab.unmount();
    });

    expect(firstSource.closed).toBe(true);
    expect(navigator.sendBeacon).toHaveBeenCalledWith(
      expect.stringContaining("connectionId=conn-1"),
    );
  });

  it("이전 connectionId의 beacon과 새 연결이 뒤섞이지 않는다", async () => {
    const tab = render(<SseProvider><Consumer handlers={{}} /></SseProvider>);
    act(() => {
      FakeEventSource.instances[0].dispatch("connected", { connectionId: "conn-1" });
    });

    act(() => {
      tab.unmount();
    });
    expect(navigator.sendBeacon).toHaveBeenCalledWith(expect.stringContaining("conn-1"));

    render(<SseProvider><Consumer handlers={{}} /></SseProvider>);
    // 이전 탭의 lock 반환이 정확히 몇 틱 뒤에 처리되는지(React act()의 내부 마이크로태스크 처리 방식에 따라
    // 달라질 수 있다)에 기대지 않고, 새 탭이 실제로 leader가 될 때까지 폴링으로 기다린다.
    await waitFor(() => expect(FakeEventSource.instances).toHaveLength(2));
    act(() => {
      FakeEventSource.instances[1].dispatch("connected", { connectionId: "conn-2" });
    });

    cleanup(); // 두 번째 탭의 provider를 언마운트해 stop()이 conn-2로 beacon을 보내게 한다.

    expect(navigator.sendBeacon).toHaveBeenCalledTimes(2);
    expect(navigator.sendBeacon).toHaveBeenLastCalledWith(expect.stringContaining("conn-2"));
  });

  it("StrictMode에서도 최종적으로 연결은 하나만 살아있다", async () => {
    render(
      <StrictMode>
        <SseProvider>
          <Consumer handlers={{ event_a: vi.fn() }} />
        </SseProvider>
      </StrictMode>,
    );
    await flush();

    const openInstances = FakeEventSource.instances.filter((instance) => !instance.closed);
    expect(openInstances).toHaveLength(1);
  });

  it("연결 장애(onerror)가 나면 매칭 상태 polling을 시작한다", () => {
    render(<SseProvider><Consumer handlers={{}} /></SseProvider>);
    const source = FakeEventSource.instances[0];

    act(() => {
      source.onerror?.();
    });

    expect(useMatchingStore.getState().startMatchingPolling).toHaveBeenCalled();
  });

  it("일시 장애(readyState=CONNECTING)면 reconnecting 상태로 두고 polling을 시작한다", () => {
    const probe = createProbe();
    render(<SseProvider><probe.Probe /></SseProvider>);
    const source = FakeEventSource.instances[0];

    act(() => {
      source.fail(FakeEventSource.CONNECTING);
    });

    expect(probe.current()?.status).toBe("reconnecting");
    expect(useMatchingStore.getState().startMatchingPolling).toHaveBeenCalled();
  });

  it("영구 종료(readyState=CLOSED)면 closed 상태로 전환하고 연결·polling을 멈춘다", () => {
    const probe = createProbe();
    render(<SseProvider><probe.Probe /></SseProvider>);
    const source = FakeEventSource.instances[0];

    act(() => {
      source.fail(FakeEventSource.CLOSED);
    });

    expect(probe.current()?.status).toBe("closed");
    expect(source.closed).toBe(true);
    expect(useMatchingStore.getState().stopMatchingPolling).toHaveBeenCalled();
    expect(useMatchingStore.getState().startMatchingPolling).not.toHaveBeenCalled();
  });

  it("재연결(connected) 성공 시 leader/follower 모두 REST 상태를 동기화한다", () => {
    render(<SseProvider><Consumer handlers={{}} /></SseProvider>); // leader
    const followerProbe = createProbe();
    render(<SseProvider><followerProbe.Probe /></SseProvider>); // follower
    const source = FakeEventSource.instances[0];

    act(() => {
      source.dispatch("connected", { connectionId: "conn-1" });
    });

    expect(followerProbe.current()?.status).toBe("connected");
    expect(useMatchingStore.getState().syncCurrentMatching).toHaveBeenCalledTimes(2);
    expect(useMatchingStore.getState().stopMatchingPolling).toHaveBeenCalledTimes(2);
  });

  it("closed에서 reconnect()를 부르면 새 EventSource를 만들고 connecting으로 돌아간다", () => {
    const probe = createProbe();
    render(<SseProvider><probe.Probe /></SseProvider>);
    act(() => {
      FakeEventSource.instances[0].fail(FakeEventSource.CLOSED);
    });
    expect(probe.current()?.status).toBe("closed");

    act(() => {
      probe.current()?.reconnect();
    });

    expect(FakeEventSource.instances).toHaveLength(2);
    expect(probe.current()?.status).toBe("connecting");
  });

  it("로그아웃하면 EventSource와 disconnect beacon, 연결을 정리한다", () => {
    render(<SseProvider><Consumer handlers={{}} /></SseProvider>);
    const source = FakeEventSource.instances[0];
    act(() => {
      source.dispatch("connected", { connectionId: "conn-1" });
    });

    act(() => {
      useSessionStore.setState({ isAuthenticated: false });
    });

    expect(source.closed).toBe(true);
    expect(navigator.sendBeacon).toHaveBeenCalledWith(expect.stringContaining("conn-1"));
  });

  it("영구 종료 시 leader가 세션을 확인하고, 무효하면 follower에도 강제 로그아웃을 전달한다", async () => {
    vi.mocked(api.me).mockRejectedValue(new Error("세션 없음"));
    render(<SseProvider><Consumer handlers={{}} /></SseProvider>); // leader
    render(<SseProvider><Consumer handlers={{}} /></SseProvider>); // follower
    const source = FakeEventSource.instances[0];

    await act(async () => {
      source.fail(FakeEventSource.CLOSED);
      await Promise.resolve();
      await Promise.resolve();
    });

    expect(api.me).toHaveBeenCalled();
    // leader 자신의 강제 로그아웃은 axios 인터셉터(별도 검증 대상)가 처리하고, 이 provider는
    // 같은 브라우저의 다른 탭(follower)에 session-invalid를 전달하는 책임만 진다.
    expect(emitUnauthorized).toHaveBeenCalled();
  });

  it("closed면 안내 모달이 뜨고, '다시 연결'은 새 연결을 맺는다", () => {
    render(
      <SseProvider>
        <SseStatusBanner />
      </SseProvider>,
    );
    expect(screen.queryByText("실시간 연결이 종료됐어요")).toBeNull();

    act(() => {
      FakeEventSource.instances[0].fail(FakeEventSource.CLOSED);
    });
    expect(screen.getByRole("dialog", { name: "실시간 연결 종료 안내" })).toBeTruthy();
    expect(screen.getByText("실시간 연결이 종료됐어요")).toBeTruthy();
    // 사용자당 연결 상한이 사라졌으므로 그 문구는 더 이상 노출되지 않는다.
    expect(screen.queryByText(/다른 탭을 닫고/)).toBeNull();

    act(() => {
      screen.getByText("다시 연결").click();
    });

    expect(FakeEventSource.instances).toHaveLength(2);
    expect(screen.queryByText("실시간 연결이 종료됐어요")).toBeNull();
  });
});

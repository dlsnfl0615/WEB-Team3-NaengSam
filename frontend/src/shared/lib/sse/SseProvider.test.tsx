import { act, StrictMode, use } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { useSessionStore } from "@/shared/store/sessionStore";
import { useMatchingStore } from "@/shared/store/matchingStore";
import { SseProvider } from "./SseProvider";
import { SseStatusBanner } from "./SseStatusBanner";
import { useSse } from "./useSse";
import { SseContext, type SseContextValue, type SseHandlers, type SseStatus } from "./SseContext";

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

function Consumer({ handlers, enabled }: { handlers: SseHandlers; enabled?: boolean }) {
  useSse(handlers, { enabled });
  return null;
}

/** 테스트에서 현재 SSE 상태/컨텍스트를 읽기 위한 프로브. */
let sseCtx: SseContextValue | null = null;
let sseStatus: SseStatus | undefined;
function CtxProbe() {
  const ctx = use(SseContext);
  sseCtx = ctx;
  sseStatus = ctx?.status;
  return null;
}

beforeEach(() => {
  FakeEventSource.instances = [];
  vi.stubGlobal("EventSource", FakeEventSource);
  useSessionStore.setState({ isAuthenticated: true, hydrated: true, user: null });
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
});

describe("SseProvider", () => {
  it("여러 useSse 호출에도 EventSource는 하나만 만든다", () => {
    render(
      <SseProvider>
        <Consumer handlers={{ event_a: vi.fn() }} />
        <Consumer handlers={{ event_b: vi.fn() }} />
      </SseProvider>,
    );

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

  it("로그아웃하면 EventSource를 종료한다", () => {
    render(
      <SseProvider>
        <Consumer handlers={{ event_a: vi.fn() }} />
      </SseProvider>,
    );
    const source = FakeEventSource.instances[0];
    expect(source.closed).toBe(false);

    act(() => {
      useSessionStore.setState({ isAuthenticated: false });
    });

    expect(source.closed).toBe(true);
  });

  it("StrictMode에서도 최종적으로 연결은 하나만 살아있다", () => {
    render(
      <StrictMode>
        <SseProvider>
          <Consumer handlers={{ event_a: vi.fn() }} />
        </SseProvider>
      </StrictMode>,
    );

    const openInstances = FakeEventSource.instances.filter((instance) => !instance.closed);
    expect(openInstances).toHaveLength(1);
  });

  it("연결 장애(onerror)가 나면 매칭 상태 polling을 시작한다", () => {
    render(
      <SseProvider>
        <Consumer handlers={{ event_a: vi.fn() }} />
      </SseProvider>,
    );
    const source = FakeEventSource.instances[0];

    act(() => {
      source.onerror?.();
    });

    expect(useMatchingStore.getState().startMatchingPolling).toHaveBeenCalled();
  });

  it("연결(재연결)이 서면 polling을 멈추고 즉시 상태를 동기화한다", () => {
    render(
      <SseProvider>
        <Consumer handlers={{ event_a: vi.fn() }} />
      </SseProvider>,
    );
    const source = FakeEventSource.instances[0];

    act(() => {
      source.dispatch("connected", {});
    });

    expect(useMatchingStore.getState().stopMatchingPolling).toHaveBeenCalled();
    expect(useMatchingStore.getState().syncCurrentMatching).toHaveBeenCalled();
  });

  it("일시 장애(readyState=CONNECTING)면 reconnecting 상태로 두고 polling을 시작한다", () => {
    render(
      <SseProvider>
        <CtxProbe />
      </SseProvider>,
    );
    const source = FakeEventSource.instances[0];

    act(() => {
      source.fail(FakeEventSource.CONNECTING);
    });

    expect(sseStatus).toBe("reconnecting");
    expect(useMatchingStore.getState().startMatchingPolling).toHaveBeenCalled();
  });

  it("영구 종료(readyState=CLOSED)면 closed 상태로 전환하고 연결·polling을 멈춘다", () => {
    render(
      <SseProvider>
        <CtxProbe />
      </SseProvider>,
    );
    const source = FakeEventSource.instances[0];

    act(() => {
      source.fail(FakeEventSource.CLOSED);
    });

    expect(sseStatus).toBe("closed");
    expect(source.closed).toBe(true);
    expect(useMatchingStore.getState().stopMatchingPolling).toHaveBeenCalled();
    expect(useMatchingStore.getState().startMatchingPolling).not.toHaveBeenCalled();
  });

  it("closed에서 reconnect()를 부르면 새 EventSource를 만들고 connecting으로 돌아간다", () => {
    render(
      <SseProvider>
        <CtxProbe />
      </SseProvider>,
    );
    act(() => {
      FakeEventSource.instances[0].fail(FakeEventSource.CLOSED);
    });
    expect(sseStatus).toBe("closed");

    act(() => {
      sseCtx?.reconnect();
    });

    expect(FakeEventSource.instances).toHaveLength(2);
    expect(sseStatus).toBe("connecting");
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

    act(() => {
      screen.getByText("다시 연결").click();
    });

    expect(FakeEventSource.instances).toHaveLength(2);
    expect(screen.queryByText("실시간 연결이 종료됐어요")).toBeNull();
  });
});

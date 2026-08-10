import { act, StrictMode } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, render } from "@testing-library/react";
import { useSessionStore } from "@/shared/store/sessionStore";
import { useMatchingStore } from "@/shared/store/matchingStore";
import { SseProvider } from "./SseProvider";
import { useSse } from "./useSse";
import type { SseHandlers } from "./SseContext";

/** 실제 EventSource 대신 테스트에서 직접 이벤트를 흘려보낼 수 있는 가짜 구현. */
class FakeEventSource {
  static instances: FakeEventSource[] = [];
  listenersByType = new Map<string, Set<(event: MessageEvent) => void>>();
  closed = false;
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
});

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act, renderHook } from "@testing-library/react";
import type { SseStatus } from "./SseContext";
import {
  SSE_RECONNECT_POLL_INTERVAL_MS,
  useSseReconnectSync,
} from "./useSseReconnectSync";

/** status/enabled를 rerender로 바꿔가며 훅을 구동하는 헬퍼. */
function setup(initial: { status: SseStatus; enabled?: boolean }) {
  const recover = vi.fn();
  const view = renderHook(
    ({ status, enabled = true }: { status: SseStatus; enabled?: boolean }) =>
      useSseReconnectSync(status, recover, { enabled }),
    { initialProps: initial },
  );
  return { recover, ...view };
}

beforeEach(() => {
  vi.useFakeTimers();
});

afterEach(() => {
  vi.useRealTimers();
});

describe("useSseReconnectSync", () => {
  it("최초 connecting→connected(마운트)에는 복구하지 않는다", () => {
    const { recover, rerender } = setup({ status: "connecting" });

    rerender({ status: "connected" });

    expect(recover).not.toHaveBeenCalled();
  });

  it("일시 장애(reconnecting) 동안 주기적으로 복구를 호출한다", () => {
    const { recover } = setup({ status: "reconnecting" });

    act(() => {
      vi.advanceTimersByTime(SSE_RECONNECT_POLL_INTERVAL_MS * 2);
    });

    expect(recover).toHaveBeenCalledTimes(2);
  });

  it("reconnecting→connected로 복귀하면 폴링을 멈추고 마지막으로 1회 복구한다", () => {
    const { recover, rerender } = setup({ status: "connected" });

    rerender({ status: "reconnecting" });
    act(() => {
      vi.advanceTimersByTime(SSE_RECONNECT_POLL_INTERVAL_MS);
    });
    expect(recover).toHaveBeenCalledTimes(1); // 인터벌 1회

    rerender({ status: "connected" });
    expect(recover).toHaveBeenCalledTimes(2); // 재연결 완료 1회

    // 재연결 후에는 인터벌이 멈춰 더 이상 호출되지 않는다.
    act(() => {
      vi.advanceTimersByTime(SSE_RECONNECT_POLL_INTERVAL_MS * 3);
    });
    expect(recover).toHaveBeenCalledTimes(2);
  });

  it("수동 재연결(closed→connecting→connected)에도 1회 복구한다", () => {
    const { recover, rerender } = setup({ status: "connected" });

    rerender({ status: "closed" });
    rerender({ status: "connecting" });
    rerender({ status: "connected" });

    expect(recover).toHaveBeenCalledTimes(1);
  });

  it("enabled=false면 아무 것도 하지 않는다", () => {
    const { recover, rerender } = setup({
      status: "reconnecting",
      enabled: false,
    });

    act(() => {
      vi.advanceTimersByTime(SSE_RECONNECT_POLL_INTERVAL_MS * 2);
    });
    rerender({ status: "connected", enabled: false });

    expect(recover).not.toHaveBeenCalled();
  });
});

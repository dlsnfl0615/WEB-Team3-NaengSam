import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act, renderHook } from "@testing-library/react";
import { useExpiryCountdown } from "./useExpiryCountdown";

const BASE = new Date("2026-08-11T10:00:00.000Z").getTime();

function iso(ms: number): string {
  return new Date(ms).toISOString();
}

function setVisibility(state: DocumentVisibilityState) {
  Object.defineProperty(document, "visibilityState", {
    configurable: true,
    get: () => state,
  });
}

beforeEach(() => {
  vi.useFakeTimers();
  vi.setSystemTime(BASE);
  setVisibility("visible");
});

afterEach(() => {
  vi.useRealTimers();
});

describe("useExpiryCountdown", () => {
  it("네트워크 지연을 반영한다 - 마운트 시점에 이미 흐른 시간만큼 줄어든 값을 반환한다", () => {
    // offeredAt이 5초 전(SSE 전송이 지연됐다고 가정), 총 30초 ttl.
    const startedAt = iso(BASE - 5000);
    const expiresAt = iso(BASE + 25000);

    const { result } = renderHook(() => useExpiryCountdown(startedAt, expiresAt));

    expect(result.current.remainingSeconds).toBe(25);
    expect(result.current.progressPercent).toBeCloseTo((25 / 30) * 100, 5);
    expect(result.current.expired).toBe(false);
  });

  it("두 인스턴스는 서로 다른 만료시각으로 독립적으로 감소한다", () => {
    const call = renderHook(() =>
      useExpiryCountdown(iso(BASE), iso(BASE + 30000)),
    );
    const offer = renderHook(() =>
      useExpiryCountdown(iso(BASE), iso(BASE + 10000)),
    );

    act(() => {
      vi.advanceTimersByTime(4000);
    });

    expect(call.result.current.remainingSeconds).toBe(26);
    expect(offer.result.current.remainingSeconds).toBe(6);
  });

  it("이미 만료된 입력이면 0~100% 범위 아래로 내려가지 않는다", () => {
    const { result } = renderHook(() =>
      useExpiryCountdown(iso(BASE - 60000), iso(BASE - 30000)),
    );

    expect(result.current.remainingSeconds).toBe(0);
    expect(result.current.progressPercent).toBe(0);
    expect(result.current.expired).toBe(true);
  });

  it("백그라운드 복귀 시 interval을 기다리지 않고 즉시 보정한다", () => {
    const { result } = renderHook(() =>
      useExpiryCountdown(iso(BASE), iso(BASE + 30000)),
    );
    expect(result.current.remainingSeconds).toBe(30);

    // 탭이 백그라운드였던 동안 interval은 쉬었다고 가정 - advanceTimersByTime 없이 시스템 시간만 건너뛴다.
    vi.setSystemTime(BASE + 20000);
    setVisibility("visible");
    act(() => {
      document.dispatchEvent(new Event("visibilitychange"));
    });

    expect(result.current.remainingSeconds).toBe(10);
  });

  it("만료 콜백은 정확히 1회만 호출된다", () => {
    const onExpire = vi.fn();
    renderHook(() => useExpiryCountdown(iso(BASE), iso(BASE + 3000), { onExpire }));

    act(() => {
      vi.advanceTimersByTime(3000);
    });
    expect(onExpire).toHaveBeenCalledTimes(1);

    act(() => {
      vi.advanceTimersByTime(5000);
    });
    expect(onExpire).toHaveBeenCalledTimes(1);
  });

  it("unmount하면 더 이상 재계산·onExpire를 하지 않는다", () => {
    const onExpire = vi.fn();
    const { unmount } = renderHook(() =>
      useExpiryCountdown(iso(BASE), iso(BASE + 3000), { onExpire }),
    );

    unmount();

    act(() => {
      vi.advanceTimersByTime(10000);
    });
    expect(onExpire).not.toHaveBeenCalled();
  });

  it("SSE 종료 등으로 값이 사라지면(undefined) 타이머를 정리하고 비활성 상태를 반환한다", () => {
    type Props = { startedAt: string | undefined; expiresAt: string | undefined };
    const { result, rerender } = renderHook<ReturnType<typeof useExpiryCountdown>, Props>(
      ({ startedAt, expiresAt }) => useExpiryCountdown(startedAt, expiresAt),
      { initialProps: { startedAt: iso(BASE), expiresAt: iso(BASE + 30000) } },
    );
    expect(result.current.remainingSeconds).toBe(30);

    rerender({ startedAt: undefined, expiresAt: undefined });

    expect(result.current).toEqual({
      remainingSeconds: 0,
      progressPercent: 0,
      expired: false,
    });
  });
});

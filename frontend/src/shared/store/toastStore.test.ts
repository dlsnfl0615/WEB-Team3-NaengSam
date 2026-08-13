import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { TOAST_DURATION_MS, useToastStore } from "./toastStore";

beforeEach(() => {
  vi.useFakeTimers();
  useToastStore.getState().clear();
});

afterEach(() => {
  useToastStore.getState().clear();
  vi.useRealTimers();
});

describe("toastStore", () => {
  it("일회성_토스트는_기본_시간_뒤에_자동으로_사라진다", () => {
    useToastStore.getState().show({ title: "알림" });

    vi.advanceTimersByTime(TOAST_DURATION_MS);

    expect(useToastStore.getState().toasts).toHaveLength(0);
  });

  it("같은_dedupeKey는_쌓지_않고_제자리에서_교체한다", () => {
    const firstId = useToastStore.getState().show({
      title: "이전 알림",
      dedupeKey: "matching",
    });
    const secondId = useToastStore.getState().show({
      title: "새 알림",
      dedupeKey: "matching",
    });

    expect(secondId).toBe(firstId);
    expect(useToastStore.getState().toasts).toEqual([
      expect.objectContaining({ id: firstId, title: "새 알림" }),
    ]);
  });

  it("같은_dedupeKey를_교체하면_자동_소멸_타이머를_다시_시작한다", () => {
    useToastStore.getState().show({ title: "이전 알림", dedupeKey: "matching" });
    vi.advanceTimersByTime(TOAST_DURATION_MS - 1_000);

    useToastStore.getState().show({ title: "새 알림", dedupeKey: "matching" });
    vi.advanceTimersByTime(1_000);

    expect(useToastStore.getState().toasts).toHaveLength(1);

    vi.advanceTimersByTime(TOAST_DURATION_MS - 1_000);
    expect(useToastStore.getState().toasts).toHaveLength(0);
  });

  it("네_번째_토스트가_오면_가장_오래된_토스트를_제거한다", () => {
    const oldestId = useToastStore.getState().show({ title: "첫 번째" });
    useToastStore.getState().show({ title: "두 번째" });
    useToastStore.getState().show({ title: "세 번째" });
    useToastStore.getState().show({ title: "네 번째" });

    const toasts = useToastStore.getState().toasts;
    expect(toasts).toHaveLength(3);
    expect(toasts.some((toast) => toast.id === oldestId)).toBe(false);
    expect(toasts.map((toast) => toast.title)).toEqual([
      "두 번째",
      "세 번째",
      "네 번째",
    ]);
  });

  it("persistent_토스트는_시간이_지나도_유지된다", () => {
    useToastStore.getState().show({ title: "확인 필요", persistent: true });

    vi.advanceTimersByTime(TOAST_DURATION_MS * 2);

    expect(useToastStore.getState().toasts).toHaveLength(1);
  });
});

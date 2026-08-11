import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { api } from "@/shared/api";
import type { CurrentMatchingStatusDto } from "@/shared/api";
import {
  MATCHING_POLL_INTERVAL_MS,
  useMatchingStore,
} from "./matchingStore";

vi.mock("@/shared/api", () => ({
  api: {
    getCurrentStatus: vi.fn(),
    getProfile: vi.fn(),
  },
  isApiError: () => false,
}));

const getCurrentStatus = vi.mocked(api.getCurrentStatus);
const getProfile = vi.mocked(api.getProfile);

/** getCurrentStatus 응답(CommonResponse 봉투)을 만든다. */
function snapshot(result: CurrentMatchingStatusDto) {
  return { isSuccess: true, code: "COM200", message: "ok", result };
}

/** pendingOffer 하나가 걸린 스냅샷. */
function pendingSnapshot(offerId: string, orderId = "order-1") {
  return snapshot({
    pendingOffer: { offerId, orderSummary: { orderId, deliveryEta: 15, deliveryAmount: 3000 } },
  });
}

beforeEach(() => {
  vi.useFakeTimers();
  getCurrentStatus.mockReset();
  getProfile.mockReset();
  getProfile.mockResolvedValue({ result: {} } as never);
  getCurrentStatus.mockResolvedValue(snapshot({}) as never);
  useMatchingStore.setState({
    pendingOffer: null,
    incomingDreami: null,
    submitting: false,
    nearbyCalls: [],
    message: null,
  });
  useMatchingStore.getState().stopMatchingPolling();
});

afterEach(() => {
  useMatchingStore.getState().stopMatchingPolling();
  vi.useRealTimers();
});

describe("matchingStore polling 복구", () => {
  it("장애 시 startMatchingPolling은 즉시 1회 + 주기마다 동기화한다", async () => {
    useMatchingStore.getState().startMatchingPolling();

    // 시작 즉시 1회
    await vi.advanceTimersByTimeAsync(0);
    expect(getCurrentStatus).toHaveBeenCalledTimes(1);

    await vi.advanceTimersByTimeAsync(MATCHING_POLL_INTERVAL_MS);
    expect(getCurrentStatus).toHaveBeenCalledTimes(2);

    await vi.advanceTimersByTimeAsync(MATCHING_POLL_INTERVAL_MS);
    expect(getCurrentStatus).toHaveBeenCalledTimes(3);
  });

  it("재연결 시 stopMatchingPolling하면 이후 동기화가 멈춘다", async () => {
    useMatchingStore.getState().startMatchingPolling();
    await vi.advanceTimersByTimeAsync(0);
    expect(getCurrentStatus).toHaveBeenCalledTimes(1);

    useMatchingStore.getState().stopMatchingPolling();

    await vi.advanceTimersByTimeAsync(MATCHING_POLL_INTERVAL_MS * 3);
    expect(getCurrentStatus).toHaveBeenCalledTimes(1);
  });

  it("재연결 직후 syncCurrentMatching은 interval 대기 없이 즉시 상태를 맞춘다", async () => {
    getCurrentStatus.mockResolvedValue(pendingSnapshot("offer-1") as never);

    await useMatchingStore.getState().syncCurrentMatching();

    expect(getCurrentStatus).toHaveBeenCalledTimes(1);
    expect(useMatchingStore.getState().pendingOffer?.offerId).toBe("offer-1");
  });

  it("로그아웃(stopMatchingPolling)하면 timer가 제거된다", async () => {
    useMatchingStore.getState().startMatchingPolling();
    await vi.advanceTimersByTimeAsync(0);

    // 로그아웃 흐름에서 호출되는 정리
    useMatchingStore.getState().stopMatchingPolling();
    const callsBefore = getCurrentStatus.mock.calls.length;

    await vi.advanceTimersByTimeAsync(MATCHING_POLL_INTERVAL_MS * 5);
    expect(getCurrentStatus).toHaveBeenCalledTimes(callsBefore);
  });

  it("startMatchingPolling을 두 번 불러도 interval은 하나만 돈다", async () => {
    useMatchingStore.getState().startMatchingPolling();
    useMatchingStore.getState().startMatchingPolling();

    await vi.advanceTimersByTimeAsync(0);
    // 즉시 동기화가 두 번 실행되지 않는다(중복 시작 무시).
    expect(getCurrentStatus).toHaveBeenCalledTimes(1);

    await vi.advanceTimersByTimeAsync(MATCHING_POLL_INTERVAL_MS);
    // interval도 하나만 → 한 tick에 1회만 증가.
    expect(getCurrentStatus).toHaveBeenCalledTimes(2);
  });

  it("in-flight 동기화가 있으면 중복 요청하지 않는다", async () => {
    let resolve!: (v: unknown) => void;
    getCurrentStatus.mockReturnValue(new Promise((r) => (resolve = r)) as never);

    const first = useMatchingStore.getState().syncCurrentMatching();
    const second = useMatchingStore.getState().syncCurrentMatching();

    expect(getCurrentStatus).toHaveBeenCalledTimes(1);

    resolve(snapshot({}));
    await Promise.all([first, second]);
  });

  it("null 스냅샷을 받으면 오래된 팝업(pendingOffer·incomingDreami)을 제거한다", async () => {
    useMatchingStore.setState({
      pendingOffer: { offerId: "old", orderId: "o", deliveryAmount: null, itemName: null,
        deliveryEta: 0, deliveryDistance: null, originLatitude: null, originLongitude: null,
        originAlias: null, originAddressLine1: null, destinationLatitude: null,
        destinationLongitude: null, destinationAlias: null, destinationAddressLine1: null,
        imageKey: null, ttlSeconds: 0 },
      incomingDreami: { offerId: "old", orderId: "o", dreamiId: "d" },
    });
    getCurrentStatus.mockResolvedValue(snapshot({}) as never);

    await useMatchingStore.getState().syncCurrentMatching();

    expect(useMatchingStore.getState().pendingOffer).toBeNull();
    expect(useMatchingStore.getState().incomingDreami).toBeNull();
  });
});

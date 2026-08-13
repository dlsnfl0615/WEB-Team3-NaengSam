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
    online: false,
    pendingOffer: null,
    incomingDreami: null,
    submitting: false,
    myLocation: null,
    nearbyCalls: [],
    nearbyCallsError: null,
    message: null,
  });
  useMatchingStore.getState().stopMatchingPolling();
});

afterEach(() => {
  useMatchingStore.getState().stopMatchingPolling();
  vi.unstubAllGlobals();
  vi.useRealTimers();
});

describe("matchingStore 위치 확인", () => {
  it("GPS 권한이 거부되면 드리미 화면을 차단할 수 있는 결과를 반환한다", async () => {
    const permissionError = {
      code: 1,
      message: "User denied Geolocation",
      PERMISSION_DENIED: 1,
      POSITION_UNAVAILABLE: 2,
      TIMEOUT: 3,
    } as GeolocationPositionError;
    const getCurrentPosition = vi.fn(
      (_success: PositionCallback, error?: PositionErrorCallback | null) => {
        error?.(permissionError);
      },
    );
    vi.stubGlobal("navigator", { geolocation: { getCurrentPosition } });

    const result = await useMatchingStore.getState().loadNearbyCalls();

    expect(result).toBe("location-unavailable");
    expect(useMatchingStore.getState().nearbyCallsError).toContain("위치 권한");
  });
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
        deliveryRequest: null, offeredAt: "2026-08-11T10:00:00", expiresAt: "2026-08-11T10:00:30" },
      incomingDreami: { offerId: "old", orderId: "o", dreamiId: "d" },
    });
    getCurrentStatus.mockResolvedValue(snapshot({}) as never);

    await useMatchingStore.getState().syncCurrentMatching();

    expect(useMatchingStore.getState().pendingOffer).toBeNull();
    expect(useMatchingStore.getState().incomingDreami).toBeNull();
  });

  it("스냅샷의 offeredAt/expiresAt을 pendingOffer로 그대로 threading한다(폴링 복구 시 정확한 카운트다운의 전제)", async () => {
    getCurrentStatus.mockResolvedValue(
      snapshot({
        pendingOffer: {
          offerId: "offer-1",
          orderSummary: { orderId: "order-1", deliveryEta: 15, deliveryAmount: 3000 },
          offeredAt: "2026-08-11T10:00:00",
          expiresAt: "2026-08-11T10:00:30",
        },
      }) as never,
    );

    await useMatchingStore.getState().syncCurrentMatching();

    const pendingOffer = useMatchingStore.getState().pendingOffer;
    expect(pendingOffer?.offeredAt).toBe("2026-08-11T10:00:00");
    expect(pendingOffer?.expiresAt).toBe("2026-08-11T10:00:30");
  });

  it("receiveDreamiInfo는 acceptedAt/expiresAt을 incomingDreami에 반영한다", async () => {
    useMatchingStore.getState().receiveDreamiInfo({
      offerId: "offer-2",
      orderId: "order-2",
      dreamiId: "dreami-1",
      acceptedAt: "2026-08-11T10:00:00",
      expiresAt: "2026-08-11T10:00:30",
    });

    const incomingDreami = useMatchingStore.getState().incomingDreami;
    expect(incomingDreami?.acceptedAt).toBe("2026-08-11T10:00:00");
    expect(incomingDreami?.expiresAt).toBe("2026-08-11T10:00:30");
  });

  it("receiveDreamiInfo는 pickupEtaMinutes를 incomingDreami에 반영한다", async () => {
    useMatchingStore.getState().receiveDreamiInfo({
      offerId: "offer-3",
      orderId: "order-3",
      dreamiId: "dreami-2",
      pickupEtaMinutes: 12,
    });

    expect(useMatchingStore.getState().incomingDreami?.pickupEtaMinutes).toBe(12);
  });

  it("receiveDreamiInfo는 pickupEtaMinutes가 없으면 null/undefined를 그대로 둔다(픽업 시간 미확인)", async () => {
    useMatchingStore.getState().receiveDreamiInfo({
      offerId: "offer-4",
      orderId: "order-4",
      dreamiId: "dreami-3",
      pickupEtaMinutes: null,
    });

    expect(useMatchingStore.getState().incomingDreami?.pickupEtaMinutes).toBeNull();
  });
});

describe("matchingStore 카운트다운 만료(로컬)", () => {
  it("expirePendingOffer는 offerId가 일치할 때만 pendingOffer를 지운다", () => {
    useMatchingStore.setState({
      pendingOffer: { offerId: "offer-1", orderId: "o", deliveryAmount: null, itemName: null,
        deliveryEta: 0, deliveryDistance: null, originLatitude: null, originLongitude: null,
        originAlias: null, originAddressLine1: null, destinationLatitude: null,
        destinationLongitude: null, destinationAlias: null, destinationAddressLine1: null,
        deliveryRequest: null, offeredAt: "2026-08-11T10:00:00", expiresAt: "2026-08-11T10:00:30" },
    });

    useMatchingStore.getState().expirePendingOffer("offer-stale");
    expect(useMatchingStore.getState().pendingOffer?.offerId).toBe("offer-1");

    useMatchingStore.getState().expirePendingOffer("offer-1");
    expect(useMatchingStore.getState().pendingOffer).toBeNull();
  });

  it("expireIncomingDreami는 offerId가 일치할 때만 incomingDreami를 지운다", () => {
    useMatchingStore.setState({
      incomingDreami: { offerId: "offer-2", orderId: "o", dreamiId: "d" },
    });

    useMatchingStore.getState().expireIncomingDreami("offer-stale");
    expect(useMatchingStore.getState().incomingDreami?.offerId).toBe("offer-2");

    useMatchingStore.getState().expireIncomingDreami("offer-2");
    expect(useMatchingStore.getState().incomingDreami).toBeNull();
  });
});

describe("matchingStore 드리미 온라인 상태 복원", () => {
  it("새로고침으로 online이 false여도 서버가 등록됐다고 하면 true로 복원한다", async () => {
    // 스토어는 메모리에만 있어 새로고침하면 false로 시작하지만 매칭엔진 등록은 살아 있다.
    getCurrentStatus.mockResolvedValue(snapshot({ dreamiOnline: true }) as never);

    await useMatchingStore.getState().syncCurrentMatching();

    expect(useMatchingStore.getState().online).toBe(true);
  });

  it("서버가 등록 해제됐다고 하면 online을 false로 되돌린다", async () => {
    useMatchingStore.setState({ online: true });
    getCurrentStatus.mockResolvedValue(snapshot({ dreamiOnline: false }) as never);

    await useMatchingStore.getState().syncCurrentMatching();

    expect(useMatchingStore.getState().online).toBe(false);
  });

  it("dreamiOnline이 없는 응답은 online을 건드리지 않는다", async () => {
    useMatchingStore.setState({ online: true });
    getCurrentStatus.mockResolvedValue(snapshot({}) as never);

    await useMatchingStore.getState().syncCurrentMatching();

    expect(useMatchingStore.getState().online).toBe(true);
  });

  it("부르미 거절 이벤트는 online을 끄지 않는다(서버 등록은 유지된다)", () => {
    useMatchingStore.setState({
      online: true,
      pendingOffer: {
        offerId: "offer-1", orderId: "order-1", deliveryAmount: null, itemName: null,
        deliveryEta: 0, deliveryDistance: null, originLatitude: null, originLongitude: null,
        originAlias: null, originAddressLine1: null, destinationLatitude: null,
        destinationLongitude: null, destinationAlias: null, destinationAddressLine1: null,
        deliveryRequest: null, offeredAt: "2026-08-13T10:00:00", expiresAt: "2026-08-13T10:00:30",
      },
    });

    useMatchingStore.getState().receiveBoormiRejected({ offerId: "offer-1" });

    expect(useMatchingStore.getState().online).toBe(true);
    expect(useMatchingStore.getState().pendingOffer).toBeNull();
  });
});

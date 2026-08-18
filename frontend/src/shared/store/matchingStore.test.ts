import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { api } from "@/shared/api";
import type { CurrentMatchingStatusDto } from "@/shared/api";
import {
  MATCHING_POLL_INTERVAL_MS,
  useMatchingStore,
  type PendingOffer,
} from "./matchingStore";

vi.mock("@/shared/api", () => ({
  api: {
    getCurrentStatus: vi.fn(),
    getProfile: vi.fn(),
    acceptOffer: vi.fn(),
    rejectOffer: vi.fn(),
    confirmDreami: vi.fn(),
    rejectDreami: vi.fn(),
    goOffline: vi.fn(),
  },
  isApiError: () => false,
}));

const getCurrentStatus = vi.mocked(api.getCurrentStatus);
const getProfile = vi.mocked(api.getProfile);
const acceptOffer = vi.mocked(api.acceptOffer);
const rejectOfferApi = vi.mocked(api.rejectOffer);
const confirmDreamiApi = vi.mocked(api.confirmDreami);
const rejectDreamiApi = vi.mocked(api.rejectDreami);
const goOfflineApi = vi.mocked(api.goOffline);

/** 응답 순서를 테스트 코드에서 직접 통제하기 위한 deferred promise. */
function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
}

/** 필수 필드만 채운 pendingOffer(테스트에서 쓰지 않는 값은 전부 null). */
function offer(overrides: Partial<PendingOffer> = {}): PendingOffer {
  return {
    offerId: "offer-1", orderId: "order-1", deliveryAmount: null, itemName: null,
    deliveryEta: 0, deliveryDistance: null, originLatitude: null, originLongitude: null,
    originAlias: null, originAddressLine1: null, destinationLatitude: null,
    destinationLongitude: null, destinationAlias: null, destinationAddressLine1: null,
    deliveryRequest: null, offeredAt: "2026-08-13T10:00:00", expiresAt: "2026-08-13T10:00:30",
    ...overrides,
  };
}

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
  acceptOffer.mockReset();
  rejectOfferApi.mockReset();
  confirmDreamiApi.mockReset();
  rejectDreamiApi.mockReset();
  goOfflineApi.mockReset();
  acceptOffer.mockResolvedValue(undefined as never);
  rejectOfferApi.mockResolvedValue(undefined as never);
  confirmDreamiApi.mockResolvedValue(undefined as never);
  rejectDreamiApi.mockResolvedValue(undefined as never);
  goOfflineApi.mockResolvedValue(undefined as never);
  getProfile.mockResolvedValue({ result: {} } as never);
  getCurrentStatus.mockResolvedValue(snapshot({}) as never);
  useMatchingStore.setState({
    online: false,
    pendingOffer: null,
    incomingDreami: null,
    awaitingBoormi: null,
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

  it("스냅샷의 offerPolicy를 pendingOffer로 그대로 threading한다(폴링 복구 시 픽업거리·확장 안내의 전제)", async () => {
    getCurrentStatus.mockResolvedValue(
      snapshot({
        pendingOffer: {
          offerId: "offer-1",
          orderSummary: { orderId: "order-1", deliveryEta: 15, deliveryAmount: 3000 },
          offeredAt: "2026-08-11T10:00:00",
          expiresAt: "2026-08-11T10:00:30",
          offerPolicy: {
            scopeKeySeconds: 60,
            evaluatedAt: "2026-08-11T10:00:00",
            orderWaitingSeconds: 61,
            pickupDistanceMeters: 4000,
            maxPickupDistanceMeters: 6000,
          },
        },
      }) as never,
    );

    await useMatchingStore.getState().syncCurrentMatching();

    expect(useMatchingStore.getState().pendingOffer?.offerPolicy).toEqual({
      scopeKeySeconds: 60,
      evaluatedAt: "2026-08-11T10:00:00",
      orderWaitingSeconds: 61,
      pickupDistanceMeters: 4000,
      maxPickupDistanceMeters: 6000,
    });
  });

  it("receiveOfferPopup(SSE)은 offerPolicy를 pendingOffer에 그대로 반영한다", () => {
    useMatchingStore.getState().receiveOfferPopup(
      offer({
        offerPolicy: {
          scopeKeySeconds: 0,
          evaluatedAt: "2026-08-13T10:00:00",
          orderWaitingSeconds: 5,
          pickupDistanceMeters: 800,
          maxPickupDistanceMeters: 3000,
        },
      }),
    );

    expect(useMatchingStore.getState().pendingOffer?.offerPolicy).toEqual({
      scopeKeySeconds: 0,
      evaluatedAt: "2026-08-13T10:00:00",
      orderWaitingSeconds: 5,
      pickupDistanceMeters: 800,
      maxPickupDistanceMeters: 3000,
    });
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
    useMatchingStore.setState({ online: true, pendingOffer: offer() });

    useMatchingStore.getState().receiveBoormiRejected({ offerId: "offer-1" });

    expect(useMatchingStore.getState().online).toBe(true);
    expect(useMatchingStore.getState().pendingOffer).toBeNull();
  });
});

/**
 * 드리미가 수락한 뒤 부르미 확정을 기다리는 구간. 백엔드에 이 대기를 알려주는 상태가 없어서
 * 프론트가 수락 성공 시점에 만들고 결말 이벤트로 지운다 — 그래서 "언제 사라지는가"가 핵심이다.
 */
describe("matchingStore 부르미 응답 대기", () => {
  it("콜을 수락하면 대기 상태가 생기고 콜 카드는 내려간다", async () => {
    useMatchingStore.setState({ pendingOffer: offer({ itemName: "설계도면" }) });

    await useMatchingStore.getState().acceptOffer();

    const { pendingOffer, awaitingBoormi } = useMatchingStore.getState();
    expect(pendingOffer).toBeNull();
    expect(awaitingBoormi).toMatchObject({
      offerId: "offer-1",
      orderId: "order-1",
      itemName: "설계도면",
    });
  });

  it("수락이 실패하면 대기 상태를 만들지 않는다", async () => {
    acceptOffer.mockRejectedValue(new Error("이미 마감된 제안입니다."));
    useMatchingStore.setState({ pendingOffer: offer() });

    await useMatchingStore.getState().acceptOffer();

    expect(useMatchingStore.getState().awaitingBoormi).toBeNull();
  });

  it("부르미가 거절하면 대기 창이 내려간다", async () => {
    useMatchingStore.setState({ pendingOffer: offer() });
    await useMatchingStore.getState().acceptOffer();

    useMatchingStore.getState().receiveBoormiRejected({ offerId: "offer-1" });

    expect(useMatchingStore.getState().awaitingBoormi).toBeNull();
    expect(useMatchingStore.getState().message).toBe("부르미가 요청을 거절했어요.");
  });

  it("제안이 마감되면 대기 창이 내려간다", async () => {
    useMatchingStore.setState({ pendingOffer: offer() });
    await useMatchingStore.getState().acceptOffer();

    useMatchingStore
      .getState()
      .receiveOfferClosed({ offerId: "offer-1", reason: "다른 드리미가 배정됐어요." });

    expect(useMatchingStore.getState().awaitingBoormi).toBeNull();
    expect(useMatchingStore.getState().message).toBe("다른 드리미가 배정됐어요.");
  });

  it("다른 제안의 거절 이벤트는 내 대기 창을 건드리지 않는다", async () => {
    useMatchingStore.setState({ pendingOffer: offer() });
    await useMatchingStore.getState().acceptOffer();

    useMatchingStore.getState().receiveBoormiRejected({ offerId: "다른-offer" });

    expect(useMatchingStore.getState().awaitingBoormi?.offerId).toBe("offer-1");
  });

  it("아무 이벤트도 못 받고 TTL이 지나면 스스로 만료된다", async () => {
    useMatchingStore.setState({ pendingOffer: offer() });
    await useMatchingStore.getState().acceptOffer();

    useMatchingStore.getState().expireAwaitingBoormi("offer-1");

    expect(useMatchingStore.getState().awaitingBoormi).toBeNull();
    expect(useMatchingStore.getState().message).toContain("응답을 받지 못했어요");
  });

  it("로그아웃하면 대기 창도 함께 정리된다", async () => {
    useMatchingStore.setState({ pendingOffer: offer() });
    await useMatchingStore.getState().acceptOffer();

    useMatchingStore.getState().clearOffers();

    expect(useMatchingStore.getState().awaitingBoormi).toBeNull();
  });
});

/**
 * accept/reject API 응답을 기다리는 사이 SSE로 새 offer가 도착해 pendingOffer가 교체되는 경쟁 조건.
 * 요청 시작 시점의 offerId를 캡처해두고, 응답 이후에는 그 값으로만 "지금도 같은 offer인지"를 판단해야
 * 방금 응답이 온 옛 offer가 이미 화면에 있는 새 offer를 지워버리는 사고를 막을 수 있다.
 */
describe("matchingStore accept/reject 경쟁 조건", () => {
  it("offer-1 accept 진행 중 offer-2가 도착하면, offer-1이 뒤늦게 성공해도 offer-2를 유지하고 offer-1 대기 상태는 만들지 않는다", async () => {
    const { promise, resolve } = deferred<void>();
    acceptOffer.mockReturnValue(promise as never);
    useMatchingStore.setState({ pendingOffer: offer({ offerId: "offer-1", orderId: "order-1" }) });

    const acceptPromise = useMatchingStore.getState().acceptOffer();
    // offer-1 accept 응답이 오기 전에 offer-2 SSE가 먼저 도착한다.
    useMatchingStore
      .getState()
      .receiveOfferPopup({ ...offer({ offerId: "offer-2", orderId: "order-2" }) });

    resolve();
    await acceptPromise;

    const { pendingOffer, awaitingBoormi, submitting } = useMatchingStore.getState();
    expect(pendingOffer?.offerId).toBe("offer-2");
    expect(awaitingBoormi).toBeNull();
    expect(submitting).toBe(false);
  });

  it("offer-1 reject 진행 중 offer-2가 도착하면, offer-1 거절이 뒤늦게 성공해도 offer-2를 유지한다", async () => {
    const { promise, resolve } = deferred<void>();
    rejectOfferApi.mockReturnValue(promise as never);
    useMatchingStore.setState({ pendingOffer: offer({ offerId: "offer-1", orderId: "order-1" }) });

    const rejectPromise = useMatchingStore.getState().rejectOffer();
    useMatchingStore
      .getState()
      .receiveOfferPopup({ ...offer({ offerId: "offer-2", orderId: "order-2" }) });

    resolve();
    await rejectPromise;

    const { pendingOffer, submitting } = useMatchingStore.getState();
    expect(pendingOffer?.offerId).toBe("offer-2");
    expect(submitting).toBe(false);
  });

  it("offer-1 accept가 뒤늦게 실패해도 그 사이 도착한 offer-2를 지우지 않는다", async () => {
    const { promise, reject } = deferred<void>();
    acceptOffer.mockReturnValue(promise as never);
    useMatchingStore.setState({ pendingOffer: offer({ offerId: "offer-1", orderId: "order-1" }) });

    const acceptPromise = useMatchingStore.getState().acceptOffer();
    useMatchingStore
      .getState()
      .receiveOfferPopup({ ...offer({ offerId: "offer-2", orderId: "order-2" }) });

    reject(new Error("이미 만료된 제안이에요."));
    await acceptPromise;

    const { pendingOffer, awaitingBoormi, submitting } = useMatchingStore.getState();
    expect(pendingOffer?.offerId).toBe("offer-2");
    expect(awaitingBoormi).toBeNull();
    expect(submitting).toBe(false);
  });

  it("이미 교체된 이전 offer의 offer_error는 새 pending offer를 지우거나 오류로 표시하지 않는다", () => {
    useMatchingStore.setState({ pendingOffer: offer({ offerId: "offer-2", orderId: "order-2" }) });

    useMatchingStore
      .getState()
      .receiveOfferError({ offerId: "offer-1", message: "이미 종료된 제안입니다." });

    const { pendingOffer, message } = useMatchingStore.getState();
    expect(pendingOffer?.offerId).toBe("offer-2");
    expect(message).toBeNull();
  });

  it("현재 pendingOffer와 offerId가 일치하는 offer_error만 지우고 안내를 띄운다", () => {
    useMatchingStore.setState({ pendingOffer: offer({ offerId: "offer-1" }) });

    useMatchingStore
      .getState()
      .receiveOfferError({ offerId: "offer-1", message: "이미 종료된 제안입니다." });

    const { pendingOffer, message } = useMatchingStore.getState();
    expect(pendingOffer).toBeNull();
    expect(message).toBe("이미 종료된 제안입니다.");
  });

  it("현재 awaitingBoormi와 offerId가 일치하는 offer_error는 대기 상태만 지우고 다른 pendingOffer는 건드리지 않는다", () => {
    useMatchingStore.setState({
      pendingOffer: offer({ offerId: "offer-2", orderId: "order-2" }),
      awaitingBoormi: {
        offerId: "offer-1",
        orderId: "order-1",
        itemName: null,
        acceptedAt: "2026-08-13T10:00:00",
        expiresAt: "2026-08-13T10:00:30",
      },
    });

    useMatchingStore.getState().receiveOfferError({ offerId: "offer-1", message: "만료됐어요." });

    const { pendingOffer, awaitingBoormi } = useMatchingStore.getState();
    expect(awaitingBoormi).toBeNull();
    expect(pendingOffer?.offerId).toBe("offer-2");
  });

  it("accept/reject의 성공·실패 모든 경로에서 submitting은 항상 false로 복구된다", async () => {
    useMatchingStore.setState({ pendingOffer: offer({ offerId: "offer-a" }) });
    await useMatchingStore.getState().acceptOffer();
    expect(useMatchingStore.getState().submitting).toBe(false);

    acceptOffer.mockRejectedValueOnce(new Error("실패"));
    useMatchingStore.setState({ pendingOffer: offer({ offerId: "offer-b" }) });
    await useMatchingStore.getState().acceptOffer();
    expect(useMatchingStore.getState().submitting).toBe(false);

    useMatchingStore.setState({ pendingOffer: offer({ offerId: "offer-c" }) });
    await useMatchingStore.getState().rejectOffer();
    expect(useMatchingStore.getState().submitting).toBe(false);

    rejectOfferApi.mockRejectedValueOnce(new Error("실패"));
    useMatchingStore.setState({ pendingOffer: offer({ offerId: "offer-d" }) });
    await useMatchingStore.getState().rejectOffer();
    expect(useMatchingStore.getState().submitting).toBe(false);
  });
});

/**
 * syncCurrentMatching의 HTTP 응답과 SSE 이벤트 사이의 순서 역전 방어. 요청이 나가 있는 사이 SSE로
 * offer 상태가 바뀌면, 뒤늦게 도착한(이미 낡은) HTTP snapshot이 그 최신 상태를 덮어쓰면 안 된다.
 */
describe("matchingStore snapshot·SSE 순서 역전 방어", () => {
  it("snapshot 요청 중 offer-2 SSE가 오면, offer-1 snapshot이 뒤늦게 응답해도 offer-2를 유지한다", async () => {
    const { promise, resolve } = deferred<{ result: CurrentMatchingStatusDto }>();
    getCurrentStatus.mockReturnValue(promise as never);

    const syncPromise = useMatchingStore.getState().syncCurrentMatching();
    // 요청이 나가 있는 사이 offer-2가 SSE로 도착해 pendingOffer가 교체된다.
    useMatchingStore.getState().receiveOfferPopup({ ...offer({ offerId: "offer-2", orderId: "order-2" }) });

    resolve(pendingSnapshot("offer-1") as never);
    await syncPromise;

    expect(useMatchingStore.getState().pendingOffer?.offerId).toBe("offer-2");
  });

  it("snapshot 요청 중 offer-2 SSE가 오면, null snapshot이 뒤늦게 응답해도 offer-2를 지우지 않는다", async () => {
    const { promise, resolve } = deferred<{ result: CurrentMatchingStatusDto }>();
    getCurrentStatus.mockReturnValue(promise as never);

    const syncPromise = useMatchingStore.getState().syncCurrentMatching();
    useMatchingStore.getState().receiveOfferPopup({ ...offer({ offerId: "offer-2", orderId: "order-2" }) });

    resolve(snapshot({}) as never); // 요청 시점 기준 pendingOffer가 없었다는 낡은 snapshot
    await syncPromise;

    expect(useMatchingStore.getState().pendingOffer?.offerId).toBe("offer-2");
  });

  it("요청 중 SSE 변경이 없으면 snapshot이 정상 적용된다", async () => {
    getCurrentStatus.mockResolvedValue(pendingSnapshot("offer-1") as never);

    await useMatchingStore.getState().syncCurrentMatching();

    expect(useMatchingStore.getState().pendingOffer?.offerId).toBe("offer-1");
  });

  it("SSE 변경 없이 되찾은 최신 snapshot의 새 offer는 정상적으로 복구된다", async () => {
    // 진입 직후처럼 로컬 상태가 비어 있다가, 폴링으로 서버가 들고 있던 offer를 처음 되찾는 경우.
    expect(useMatchingStore.getState().pendingOffer).toBeNull();
    getCurrentStatus.mockResolvedValue(pendingSnapshot("offer-복구") as never);

    await useMatchingStore.getState().syncCurrentMatching();

    expect(useMatchingStore.getState().pendingOffer?.offerId).toBe("offer-복구");
  });

  it("부르미 incomingDreami도 동일하게 보호된다 - 낡은 snapshot이 새 dreami_info를 지우지 않는다", async () => {
    const { promise, resolve } = deferred<{ result: CurrentMatchingStatusDto }>();
    getCurrentStatus.mockReturnValue(promise as never);

    const syncPromise = useMatchingStore.getState().syncCurrentMatching();
    // 요청이 나가 있는 사이 dreami_info SSE로 offer-2에 대한 확인 대기가 새로 생긴다.
    useMatchingStore.getState().receiveDreamiInfo({
      offerId: "offer-2",
      orderId: "order-2",
      dreamiId: "dreami-2",
    });

    resolve(snapshot({})); // 요청 시점 기준 incomingDreami가 없었다는 낡은 snapshot
    await syncPromise;

    expect(useMatchingStore.getState().incomingDreami?.offerId).toBe("offer-2");
  });

  it("온라인 상태 동기화는 offer revision과 무관하게 적용된다", async () => {
    const { promise, resolve } = deferred<{ result: CurrentMatchingStatusDto }>();
    getCurrentStatus.mockReturnValue(promise as never);
    useMatchingStore.setState({ online: false });

    const syncPromise = useMatchingStore.getState().syncCurrentMatching();
    // offer revision을 바꾸는 SSE가 요청 중간에 도착해도(pendingOffer 교체) 온라인 여부는 그대로 반영돼야 한다.
    useMatchingStore.getState().receiveOfferPopup({ ...offer({ offerId: "offer-2", orderId: "order-2" }) });

    resolve(snapshot({ dreamiOnline: true }) as never);
    await syncPromise;

    const { online, pendingOffer } = useMatchingStore.getState();
    expect(online).toBe(true);
    expect(pendingOffer?.offerId).toBe("offer-2");
  });

  it("in-flight 동기화 요청 중복 방지는 그대로 유지된다", async () => {
    const { promise, resolve } = deferred<{ result: CurrentMatchingStatusDto }>();
    getCurrentStatus.mockReturnValue(promise as never);

    const first = useMatchingStore.getState().syncCurrentMatching();
    const second = useMatchingStore.getState().syncCurrentMatching();

    expect(getCurrentStatus).toHaveBeenCalledTimes(1);

    resolve(snapshot({}) as never);
    await Promise.all([first, second]);
  });

  it("확정 처리 중 도착한 낡은 snapshot이 지워진 incomingDreami를 되살리지 않는다", async () => {
    useMatchingStore.setState({
      incomingDreami: { offerId: "offer-1", orderId: "order-1", dreamiId: "d" },
    });
    const { promise, resolve } = deferred<{ result: CurrentMatchingStatusDto }>();
    getCurrentStatus.mockReturnValue(promise as never);

    // syncCurrentMatching 요청이 나가 있는 사이 확정이 완료되어 incomingDreami가 로컬에서 지워진다.
    const syncPromise = useMatchingStore.getState().syncCurrentMatching();
    await useMatchingStore.getState().confirmDreami();

    resolve(
      snapshot({
        incomingDreami: { offerId: "offer-1", orderId: "order-1", dreamiId: "d" },
      }) as never,
    );
    await syncPromise;

    expect(useMatchingStore.getState().incomingDreami).toBeNull();
  });

  it("거절 처리 중 도착한 낡은 snapshot이 지워진 incomingDreami를 되살리지 않는다", async () => {
    useMatchingStore.setState({
      incomingDreami: { offerId: "offer-1", orderId: "order-1", dreamiId: "d" },
    });
    const { promise, resolve } = deferred<{ result: CurrentMatchingStatusDto }>();
    getCurrentStatus.mockReturnValue(promise as never);

    const syncPromise = useMatchingStore.getState().syncCurrentMatching();
    await useMatchingStore.getState().rejectDreami();

    resolve(
      snapshot({
        incomingDreami: { offerId: "offer-1", orderId: "order-1", dreamiId: "d" },
      }) as never,
    );
    await syncPromise;

    expect(useMatchingStore.getState().incomingDreami).toBeNull();
  });

  it("오프라인 전환 중 도착한 낡은 snapshot이 지워진 pendingOffer를 되살리지 않는다", async () => {
    useMatchingStore.setState({ pendingOffer: offer({ offerId: "offer-1", orderId: "order-1" }) });
    const { promise, resolve } = deferred<{ result: CurrentMatchingStatusDto }>();
    getCurrentStatus.mockReturnValue(promise as never);

    const syncPromise = useMatchingStore.getState().syncCurrentMatching();
    await useMatchingStore.getState().goOffline();

    resolve(pendingSnapshot("offer-1") as never);
    await syncPromise;

    expect(useMatchingStore.getState().pendingOffer).toBeNull();
  });
});

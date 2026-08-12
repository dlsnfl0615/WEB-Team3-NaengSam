import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { api } from "@/shared/api";
import type { DeliveryDetailResponseDto } from "@/shared/api";
import { SseContext, type SseContextValue, type SseStatus } from "@/shared/lib/sse/SseContext";
import { RealDeliveryTracking } from "./RealDeliveryTracking";

// 카카오 지도는 이 테스트의 대상이 아니라 렌더만 비운다.
vi.mock("@/shared/ui", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/shared/ui")>()),
  DeliveryRouteMap: () => null,
}));

vi.mock("@/shared/api", () => ({
  api: { getDeliveryDetail: vi.fn(), cancelByBoormi: vi.fn() },
  isApiError: () => false,
  DeliveryStatusResponseDtoStatus: {
    PICKUP_NORMAL: "PICKUP_NORMAL",
    PICKUP_DELAYED: "PICKUP_DELAYED",
    DELIVERING: "DELIVERING",
    DELIVERED: "DELIVERED",
    PICKUP_CANCELLED_BY_ADMIN: "PICKUP_CANCELLED_BY_ADMIN",
  },
}));

const getDeliveryDetail = vi.mocked(api.getDeliveryDetail);

const ORDER_ID = "order-1";
const OFFLINE_TITLE = "드리미 위치를 받지 못하고 있어요";
const UNSTABLE_TITLE = "실시간 연결이 불안정해요";

/** 상세 조회 응답(CommonResponse 봉투). dreamiOffline으로 서버가 판정한 드리미 연결 상태를 준다. */
function detailResponse(overrides: Partial<DeliveryDetailResponseDto> = {}) {
  return {
    isSuccess: true,
    code: "COM200",
    message: "ok",
    result: {
      orderId: ORDER_ID,
      status: "DELIVERING",
      currentLocation: { latitude: 37.5, longitude: 127.0 },
      originLatitude: 37.49,
      originLongitude: 127.02,
      destinationLatitude: 37.51,
      destinationLongitude: 127.03,
      destinationAddressLine1: "서울시 강남구 테헤란로 123",
      routePath: [],
      deliveryRoutePath: [],
      dreamiOffline: false,
      ...overrides,
    },
  } as Awaited<ReturnType<typeof api.getDeliveryDetail>>;
}

function renderWithSse(status: SseStatus) {
  const value: SseContextValue = {
    status,
    connected: status === "connected",
    subscribe: () => () => {},
    reconnect: () => {},
  };
  return render(
    <MemoryRouter initialEntries={["/delivery-detail"]}>
      <SseContext value={value}>
        <RealDeliveryTracking orderId={ORDER_ID} />
      </SseContext>
    </MemoryRouter>,
  );
}

/**
 * 부르미 추적 화면의 상단 알림 우선순위 테스트.
 * 핵심은 "내 SSE가 끊긴 동안에는 드리미 배너를 띄우지 않는다" — 내 인터넷 장애를 드리미 장애로 오해하면 안 된다.
 */
describe("RealDeliveryTracking 상단 알림", () => {
  beforeEach(() => {
    getDeliveryDetail.mockResolvedValue(detailResponse());
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("서버가_드리미_오프라인이라고_주면_상단에_안내를_띄운다", async () => {
    getDeliveryDetail.mockResolvedValue(
      detailResponse({ dreamiOffline: true, secondsSinceLastLocation: 45 }),
    );

    renderWithSse("connected");

    expect(await screen.findByText(OFFLINE_TITLE)).toBeTruthy();
  });

  it("드리미가_정상이면_아무_안내도_띄우지_않는다", async () => {
    renderWithSse("connected");

    // 상세 조회가 끝난 뒤를 관찰하려고 화면의 다른 요소가 뜨는 걸 기다린다.
    expect(await screen.findByText("실시간 배송")).toBeTruthy();
    expect(screen.queryByText(OFFLINE_TITLE)).toBeNull();
    expect(screen.queryByText(UNSTABLE_TITLE)).toBeNull();
  });

  it("내_연결이_끊긴_동안에는_드리미_배너_대신_내_연결_안내만_보인다", async () => {
    // 서버는 드리미가 끊겼다고 알려준 상태지만, 내 SSE도 끊겨 있어 그 정보를 신뢰할 수 없다.
    getDeliveryDetail.mockResolvedValue(
      detailResponse({ dreamiOffline: true, secondsSinceLastLocation: 45 }),
    );

    renderWithSse("reconnecting");

    expect(await screen.findByText(UNSTABLE_TITLE)).toBeTruthy();
    expect(screen.queryByText(OFFLINE_TITLE)).toBeNull();
  });

  it("연결이_영구_종료된_동안에도_드리미_배너를_띄우지_않는다", async () => {
    // closed는 전역 SseStatusBanner 모달이 안내한다. 이때도 위치가 안 오는 건 내 연결 탓이라 드리미 배너는 숨긴다.
    getDeliveryDetail.mockResolvedValue(
      detailResponse({ dreamiOffline: true, secondsSinceLastLocation: 45 }),
    );

    renderWithSse("closed");

    expect(await screen.findByText("실시간 배송")).toBeTruthy();
    expect(screen.queryByText(OFFLINE_TITLE)).toBeNull();
  });

  it("최초_연결_중에는_연결_불안정_안내를_띄우지_않는다", async () => {
    renderWithSse("connecting");

    expect(await screen.findByText("실시간 배송")).toBeTruthy();
    expect(screen.queryByText(UNSTABLE_TITLE)).toBeNull();
  });
});

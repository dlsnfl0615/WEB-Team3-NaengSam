import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { DeliveryTrackScreen } from "./DeliveryTrackScreen";

const locationState = vi.hoisted(() => ({
  error: null as string | null,
  position: null,
}));

vi.mock("@/shared/ui", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/shared/ui")>()),
  DeliveryRouteMap: () => null,
}));

vi.mock("@/shared/api", () => ({
  api: { cancelByDreami: vi.fn() },
  isApiError: () => false,
  DeliveryStatusResponseDtoStatus: {
    DELIVERING: "DELIVERING",
    PICKUP_CANCELLED_BY_ADMIN: "PICKUP_CANCELLED_BY_ADMIN",
  },
}));

vi.mock("@/shared/lib", () => ({
  formatArrivalTime: () => null,
  getUntrackableDeliveryNotice: () => null,
  recallDeliveryStage: () => undefined,
  rememberDeliveryStage: vi.fn(),
  useDeliveryDetailGate: () => ({
    detail: {
      destinationAddressLine1: "서울시 강남구 테헤란로 123",
      routePath: [],
      deliveryRoutePath: [],
    },
    ready: true,
    loading: false,
    blockingModal: {
      open: false,
      message: "",
      canRetry: false,
    },
    retry: vi.fn(),
    refresh: vi.fn(),
    block: vi.fn(),
  }),
  useDreamiLocationBroadcast: () => locationState,
  useSse: () => ({ status: "connected" }),
  useSseReconnectSync: vi.fn(),
  // 연락 시트는 이 테스트(GPS 안내)의 관심사가 아니라 렌더만 비운다.
  ContactSheet: () => null,
}));

vi.mock("@/shared/store/deliveryStore", () => ({
  useActiveDelivery: () => null,
  useDeliveryStore: (selector: (state: object) => unknown) =>
    selector({
      advance: vi.fn(),
      complete: vi.fn(),
      cancel: vi.fn(),
    }),
}));

afterEach(() => {
  cleanup();
  locationState.error = null;
  vi.clearAllMocks();
});

describe("DeliveryTrackScreen GPS 알림", () => {
  it("배달_화면_진입_후_GPS가_끊기면_안내하고_복구되면_내린다", () => {
    const view = render(
      <MemoryRouter initialEntries={["/delivery-track?orderId=order-1"]}>
        <DeliveryTrackScreen />
      </MemoryRouter>,
    );

    expect(screen.queryByText("GPS를 허용해주세요.")).toBeNull();

    locationState.error = "denied";
    view.rerender(
      <MemoryRouter initialEntries={["/delivery-track?orderId=order-1"]}>
        <DeliveryTrackScreen />
      </MemoryRouter>,
    );

    expect(screen.getByText("GPS를 허용해주세요.")).toBeTruthy();

    locationState.error = null;
    view.rerender(
      <MemoryRouter initialEntries={["/delivery-track?orderId=order-1"]}>
        <DeliveryTrackScreen />
      </MemoryRouter>,
    );

    expect(screen.queryByText("GPS를 허용해주세요.")).toBeNull();
  });
});

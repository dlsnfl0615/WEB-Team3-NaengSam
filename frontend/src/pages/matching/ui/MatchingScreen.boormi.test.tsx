import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { api } from "@/shared/api";
import { useMatchingStore } from "@/shared/store/matchingStore";
import { MatchingScreen } from "./MatchingScreen";

vi.mock("@/shared/lib/role/useRole", () => ({
  useRole: () => ({ role: "부르미" }),
}));

vi.mock("@/shared/api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/shared/api")>();
  return {
    ...actual,
    api: {
      ...actual.api,
      getCurrentStatus: vi.fn(),
      getBoormiOrders: vi.fn(),
      findNearbyWaitingDreamis: vi.fn(),
    },
  };
});

const getCurrentStatus = vi.mocked(api.getCurrentStatus);
const getBoormiOrders = vi.mocked(api.getBoormiOrders);
const findNearbyWaitingDreamis = vi.mocked(api.findNearbyWaitingDreamis);

beforeEach(() => {
  getCurrentStatus.mockResolvedValue({ result: {} } as never);
  getBoormiOrders.mockResolvedValue({
    result: {
      orders: [
        {
          orderId: "order-1",
          originLatitude: 37.4979,
          originLongitude: 127.0276,
        },
      ],
    },
  } as never);
  findNearbyWaitingDreamis.mockResolvedValue({
    result: [
      {
        dreamiId: "dreami-1",
        location: { latitude: 37.498, longitude: 127.028 },
        distanceMeters: 50,
      },
    ],
  } as never);
  useMatchingStore.setState({
    online: false,
    pendingOffer: null,
    incomingDreami: null,
    message: null,
  });
});

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("MatchingScreen 부르미 주변 드리미 지도", () => {
  it("주문의 픽업 좌표를 기준으로 주변 대기 드리미를 조회한다", async () => {
    render(
      <MemoryRouter initialEntries={["/matching?orderId=order-1"]}>
        <Routes>
          <Route path="/matching" element={<MatchingScreen />} />
        </Routes>
      </MemoryRouter>,
    );

    expect(
      await screen.findByText("근방 3km 내 드리미 1명 대기중"),
    ).toBeTruthy();
    expect(findNearbyWaitingDreamis).toHaveBeenCalledWith({
      lat: 37.4979,
      lng: 127.0276,
      radius: 3000,
      count: 10,
    });
  });
});

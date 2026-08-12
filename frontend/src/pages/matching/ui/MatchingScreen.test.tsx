import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { useMatchingStore } from "@/shared/store/matchingStore";
import { MatchingScreen } from "./MatchingScreen";

vi.mock("@/shared/lib/role/useRole", () => ({
  useRole: () => ({ role: "드리미" }),
}));

beforeEach(() => {
  const permissionError = {
    code: 1,
    message: "User denied Geolocation",
    PERMISSION_DENIED: 1,
    POSITION_UNAVAILABLE: 2,
    TIMEOUT: 3,
  } as GeolocationPositionError;
  vi.stubGlobal("navigator", {
    geolocation: {
      getCurrentPosition: vi.fn(
        (_success: PositionCallback, error?: PositionErrorCallback | null) => {
          error?.(permissionError);
        },
      ),
    },
  });
  useMatchingStore.setState({
    online: false,
    myLocation: null,
    nearbyCalls: [],
    nearbyCallsError: null,
    message: null,
  });
});

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe("MatchingScreen 위치 권한 차단", () => {
  it("권한 거부 안내를 닫을 수 없고 홈으로만 이동할 수 있다", async () => {
    render(
      <MemoryRouter initialEntries={["/matching"]}>
        <Routes>
          <Route path="/matching" element={<MatchingScreen />} />
          <Route path="/home" element={<p>홈 화면</p>} />
        </Routes>
      </MemoryRouter>,
    );

    expect(
      await screen.findByRole("dialog", { name: "위치 권한이 필요해요" }),
    ).toBeTruthy();
    expect(screen.queryByRole("button", { name: "닫기" })).toBeNull();
    expect(screen.queryByRole("button", { name: "재시도하기" })).toBeNull();

    fireEvent.click(screen.getByRole("button", { name: "홈으로 돌아가기" }));

    expect(await screen.findByText("홈 화면")).toBeTruthy();
  });
});

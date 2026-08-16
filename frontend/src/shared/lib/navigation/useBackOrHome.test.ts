import { afterEach, describe, expect, it, vi } from "vitest";
import { renderHook } from "@testing-library/react";
import { ROUTES } from "@/shared/config/routes";
import { useBackOrHome } from "./useBackOrHome";

const navigate = vi.fn();

vi.mock("react-router-dom", () => ({
  useNavigate: () => navigate,
}));

/** history.state를 원하는 idx로 바꾼다(react-router가 항목마다 심는 값). */
function stubHistoryIndex(state: unknown) {
  window.history.replaceState(state, "");
}

afterEach(() => {
  navigate.mockReset();
  stubHistoryIndex(null);
});

describe("useBackOrHome", () => {
  it("앱 히스토리가 남아 있으면 한 칸 뒤로 간다", () => {
    stubHistoryIndex({ idx: 2 });
    const { result } = renderHook(() => useBackOrHome());

    result.current();

    expect(navigate).toHaveBeenCalledWith(-1);
  });

  it("앱의 첫 항목(idx 0)이면 뒤로 가지 않고 홈으로 보낸다", () => {
    // 로그인·게스트 라우트 리다이렉트는 replace로 들어와 되짚을 항목이 없다.
    stubHistoryIndex({ idx: 0 });
    const { result } = renderHook(() => useBackOrHome());

    result.current();

    expect(navigate).toHaveBeenCalledWith(ROUTES.home, { replace: true });
  });

  it("idx가 없으면(알림 클릭으로 열린 새 창 등) 홈으로 보낸다", () => {
    // clients.openWindow로 열린 창은 히스토리 0번이 about:blank라 뒤로 가면 앱을 이탈한다.
    stubHistoryIndex(null);
    const { result } = renderHook(() => useBackOrHome());

    result.current();

    expect(navigate).toHaveBeenCalledWith(ROUTES.home, { replace: true });
  });

  it("idx가 null이면 홈으로 보낸다", () => {
    stubHistoryIndex({ idx: null });
    const { result } = renderHook(() => useBackOrHome());

    result.current();

    expect(navigate).toHaveBeenCalledWith(ROUTES.home, { replace: true });
  });
});

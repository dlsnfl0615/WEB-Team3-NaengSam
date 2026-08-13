import { useCallback } from "react";
import { useNavigate } from "react-router-dom";
import { ROUTES } from "@/shared/config/routes";

/**
 * 뒤로 갈 앱 화면이 있으면 뒤로, 없으면 홈으로 보내는 뒤로가기.
 *
 * `navigate(-1)`은 앱 안이 아니라 브라우저 히스토리를 한 칸 되짚기 때문에 결과가 진입 경로에 종속된다.
 * - 알림 클릭(`clients.openWindow`)으로 열린 창은 히스토리 0번이 `about:blank`라 앱을 이탈한다.
 * - 로그인·게스트 라우트 리다이렉트는 `replace`로 들어오므로 되짚을 항목이 아예 없어 버튼이 먹통이 된다.
 *
 * react-router는 히스토리 항목마다 `idx`를 심는다. 0이면 이 화면이 앱의 첫 항목이라는 뜻이고, 그때만 홈으로 대신 보낸다.
 */
export function useBackOrHome(): () => void {
  const navigate = useNavigate();

  return useCallback(() => {
    const idx = (window.history.state as { idx?: number | null } | null)?.idx;
    if (typeof idx === "number" && idx > 0) {
      navigate(-1);
      return;
    }
    navigate(ROUTES.home, { replace: true });
  }, [navigate]);
}

import { ROUTES } from "@/shared/config/routes";
import type { AuthUser } from "@/shared/mock/types";

/**
 * 로그인 직후 보내줄 화면을 정한다.
 *
 * 진행 중인 배달이 있으면 그 추적 화면으로 곧바로 복귀시키고, 그 외에는 홈으로 보낸다.
 * 매칭 팝업이 필요한 구간(MATCHING, PENDING_BOORMI_CONFIRMATION)은 홈에서 SSE·`syncCurrentMatching`이
 * 알아서 복원하므로 별도 경로가 필요 없다.
 */
export function resolveLandingRoute(user: AuthUser | null): string {
  if (!user?.activeRole) return ROUTES.home;

  const { activeRole, activeOrderId, activeOrderCd } = user;

  // 주문이 없는데 활성이면 드리미가 오퍼를 기다리는 중이다. 매칭 화면이 온라인 상태를 이어받는다.
  if (!activeOrderId) {
    return activeRole === "DREAMI" ? ROUTES.matching : ROUTES.home;
  }

  if (activeOrderCd === "IN_PROGRESS") {
    const path =
      activeRole === "DREAMI" ? ROUTES.deliveryTrack : ROUTES.deliveryDetail;
    return `${path}?orderId=${activeOrderId}`;
  }

  return ROUTES.home;
}

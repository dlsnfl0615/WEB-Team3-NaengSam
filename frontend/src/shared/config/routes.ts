/**
 * 앱 라우트 경로 상수(SSOT).
 * 새 화면을 추가하면 여기에 한 줄만 추가하세요. navigate()·route.tsx에서 참조합니다.
 */
export const ROUTES = {
  onboarding: "/",
  login: "/login",
  signup: "/signup",
  verify: "/verify",
  home: "/home",
  requestCreate: "/request-create",
  destinationSearch: "/destination-search",
  matching: "/matching",
  rejectReason: "/reject-reason",
} as const;

export type RouteKey = keyof typeof ROUTES;

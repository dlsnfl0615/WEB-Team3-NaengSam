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
  matchingStatus: "/matching-status",
  rejectReason: "/reject-reason",
  deliveryTrack: "/delivery-track",
  deliveryDetail: "/delivery-detail",
  deliveryComplete: "/delivery-complete",
  driverReason: "/driver-reason",
  deliveryProof: "/delivery-proof",
  activity: "/activity",
  activityDetail: "/activity-detail",
  activityDetailDriver: "/activity-detail-driver",
  earnings: "/earnings",
  wallet: "/wallet",
  pointCharge: "/point-charge",
  mypage: "/mypage",
  deliveryTest: "/delivery-test",
} as const;

export type RouteKey = keyof typeof ROUTES;

/**
 * 로그인 없이 접근 가능한 공개 페이지. 이 목록에 없는 라우트는 RequireAuth로 보호됩니다.
 * 새 공개 페이지를 만들면 여기에 한 줄 추가하세요.
 */
export const PUBLIC_ROUTES: string[] = [
  ROUTES.onboarding,
  ROUTES.login,
  ROUTES.signup,
];

/**
 * 앱 라우트 경로 상수(SSOT).
 * 새 화면을 추가하면 여기에 한 줄만 추가하세요. navigate()·route.tsx에서 참조합니다.
 */
export const ROUTES = {
  onboarding: "/",
  login: "/login",
  signup: "/signup",
  verify: "/verify",
  dreamiPending: "/dreami-pending",
  home: "/home",
  requestCreate: "/request-create",
  destinationSearch: "/destination-search",
  matching: "/matching",
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
  faq: "/faq",
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

/**
 * 게스트 전용 페이지. 이미 로그인된 사용자가 접근하면 홈(/home)으로 보냅니다.
 * (PUBLIC_ROUTES의 부분집합이어야 합니다 — 로그인 없이 접근 가능한 공개 페이지 중 일부)
 */
export const GUEST_ONLY_ROUTES: string[] = [
  ROUTES.onboarding,
  ROUTES.login,
  ROUTES.signup,
];

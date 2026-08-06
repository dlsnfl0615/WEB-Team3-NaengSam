import { loadScript } from "./loadScript";

/** 카카오 JavaScript 앱키(지도 표시용). 미설정 시 지도는 폴백 처리된다. */
export const KAKAO_MAP_KEY = import.meta.env.VITE_KAKAO_MAP_KEY as
  | string
  | undefined;

const DAUM_POSTCODE_SRC =
  "//t1.daumcdn.net/mapjsapi/bundle/postcode/prod/postcode.v2.js";

/** 다음 우편번호 스크립트를 로드한다(앱키 불필요). */
export async function loadDaumPostcode(): Promise<void> {
  await loadScript(DAUM_POSTCODE_SRC);
}

/**
 * 카카오 맵 SDK(services 포함)를 로드하고 kakao.maps 준비까지 기다린다.
 * VITE_KAKAO_MAP_KEY가 없으면 null을 반환한다(지도 비활성).
 */
export async function loadKakaoMaps(): Promise<typeof window.kakao | null> {
  if (!KAKAO_MAP_KEY) return null;
  await loadScript(
    `//dapi.kakao.com/v2/maps/sdk.js?appkey=${KAKAO_MAP_KEY}&libraries=services&autoload=false`,
  );
  await new Promise<void>((resolve) => window.kakao.maps.load(() => resolve()));
  return window.kakao;
}

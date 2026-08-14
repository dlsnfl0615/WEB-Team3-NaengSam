/**
 * 절감 리포트 하단 안내 문구.
 * 요금 숫자는 백엔드 BoormiService.calculatePrice의 상수(BASE_FEE 3000원, BASE_SECTION 1500m,
 * BASE_RATE 100m당 100원)에서 가져온 값이라 요금 정책이 바뀌면 여기도 같이 고쳐야 한다.
 * 시장 환산 금액(thisMonthMarketAmount)은 서버가 건수·거리로 계산해 내려주므로 여기에 적지 않는다.
 */
export const MARKET_NOTE =
  "일반 오토바이 퀵은 서울 시내 3km 이내 기본요금이 8,000~12,000원이고, 3km를 넘으면 km당 2,000원대가 더 붙어요.";

export const OUR_PRICE_NOTE =
  "쉼,부름은 기본 3,000원에 100m당 100원이라 서류 1.5km 기준 4,500원이에요.";

export const SURVEY_BASIS = "2026년 8월 시장 조사 기준";

/**
 * 드리미와 부르미를 전국 단위로 흩뿌리기 위한 배치기.
 *
 * 원본 matchingtest는 드리미 전원을 `CENTER_LAT/LNG ± SPREAD_DEG`(강남 ±0.01°) 안에 균등 배치하고,
 * 주문은 백엔드 스텁이 강남 격자에 떨어뜨린다. 그러면 전원이 0.01° 격자 셀 9개에 들어가는데, 매칭 후보
 * 조립의 그리드 프리필터는 가장 좁은 scope(1000m)에서도 15셀을 훑으므로 걸러지는 드리미가 0명이다.
 * 즉 "전국에 흩어진 대기 드리미"라는 상황 자체가 재현되지 않는다.
 *
 * 이 모듈은 드리미와 부르미를 같은 존 목록(서울 강남 + 지방 16개 도시)에 라운드로빈으로 함께 배치한다.
 * 존마다 수요(주문)와 공급(드리미)이 같은 비율로 들어가므로 어느 도시도 굶지 않고, 도시끼리는 수백 km
 * 떨어져 있어 서로의 후보가 되지 않는다.
 *
 * ── 백엔드 설정이 반드시 짝을 이뤄야 한다 ─────────────────────────────
 * 주문 좌표는 하네스가 못 정한다. `POST /api/v1/boormi/calls`(OrderRequest)는 도로명주소 문자열만 받고,
 * 좌표는 백엔드 DevCoordinatesService가 만든다. 그 스텁을 **`KAKAO_DEV_ZONE_MODE=national`로 띄워야**
 * 주소 첫 토큰(도시명)을 보고 해당 도시에 주문을 떨어뜨린다. 기본값(gangnam)으로 띄우면 주문이 전부
 * 강남에 몰려 지방 도시의 주문이 통째로 굶는다 — run.mjs가 실행 전에 이 설정을 검사한다.
 *
 * 아래 `city` 값은 백엔드 DevCoordinatesService.NATIONAL_ZONES의 도시명과 정확히 같아야 한다.
 */

/** 서울 강남. 브라우저 계정(config/users.json)과 원본 주소 목록이 여기에 있어 항상 0번이다. */
export const ORDER_ZONE = { city: "서울", name: "서울 강남", lat: 37.4979, lng: 127.0276 };

export const NATIONAL_ZONES = [
  ORDER_ZONE,
  { city: "부산", name: "부산 서면", lat: 35.1578, lng: 129.0594 },
  { city: "대구", name: "대구 동성로", lat: 35.8686, lng: 128.594 },
  { city: "인천", name: "인천 구월동", lat: 37.4487, lng: 126.7016 },
  { city: "광주", name: "광주 상무지구", lat: 35.152, lng: 126.8515 },
  { city: "대전", name: "대전 둔산동", lat: 36.351, lng: 127.3845 },
  { city: "울산", name: "울산 삼산동", lat: 35.5384, lng: 129.3114 },
  { city: "수원", name: "수원 인계동", lat: 37.2636, lng: 127.0286 },
  { city: "제주", name: "제주 연동", lat: 33.489, lng: 126.4983 },
  { city: "강릉", name: "강릉 교동", lat: 37.7519, lng: 128.8761 },
  { city: "전주", name: "전주 서신동", lat: 35.8242, lng: 127.148 },
  { city: "청주", name: "청주 성안동", lat: 36.6424, lng: 127.489 },
  { city: "천안", name: "천안 불당동", lat: 36.8151, lng: 127.1139 },
  { city: "창원", name: "창원 상남동", lat: 35.2281, lng: 128.6811 },
  { city: "포항", name: "포항 죽도동", lat: 36.019, lng: 129.3435 },
  { city: "춘천", name: "춘천 온의동", lat: 37.8813, lng: 127.7298 },
  { city: "목포", name: "목포 하당", lat: 34.8118, lng: 126.3922 },
];

const round6 = (v) => Number(v.toFixed(6));

/**
 * i번째 에이전트가 속할 존을 정한다. 드리미와 부르미가 같은 규칙을 쓰므로 존마다 수요와 공급 비율이
 * 같아진다. local 모드에서는 전원 주문 존이라 원본 matchingtest와 완전히 같다.
 *
 * @param {object} o
 * @param {string} o.mode           "national" | "local"
 * @param {Array}  [o.zones]        존 목록. 기본 NATIONAL_ZONES.
 * @returns {(index: number) => {city: string, name: string, lat: number, lng: number}}
 */
export function createZonePicker({ mode, zones = NATIONAL_ZONES }) {
  if (mode !== "national" || zones.length === 0) return () => ORDER_ZONE;
  return (index) => zones[index % zones.length];
}

/**
 * 드리미 i번의 좌표를 정하는 함수를 만든다. 존 중심에서 `center.spread`(도) 반경으로 지터를 준다.
 * 존 중심은 백엔드가 그 도시의 주문 격자를 놓는 중심과 같으므로, 같은 존의 드리미와 주문은 서로
 * offer scope(1000~6000m) 안에 들어온다.
 */
export function createDreamiPlacer({ center, mode, zones = NATIONAL_ZONES }) {
  const pickZone = createZonePicker({ mode, zones });
  const jitter = (base) => round6(base + (Math.random() - 0.5) * 2 * center.spread);
  return (index) => {
    const zone = pickZone(index);
    return { lat: jitter(zone.lat), lng: jitter(zone.lng), zone: zone.name };
  };
}

/**
 * 환경변수에서 배치 설정을 읽어 `center` 객체를 만든다. run.mjs와 seed.mjs 단독 실행이 같은 값을 쓰도록
 * 한 곳에 모은다.
 *
 * - `DIST_MODE`      national(기본) | local. local이면 원본 matchingtest와 완전히 동일한 분포다.
 * - `CENTER_LAT/LNG` 주문 존(서울 강남) 중심. 브라우저 계정 좌표와 맞춘 값이라 바꿀 일이 없다.
 * - `SPREAD_DEG`     도시 하나 안에서의 지터 반경(도). 0.01 ≈ 위도 1.1km.
 */
export function resolveCenter(env = process.env) {
  const num = (key, fallback) => {
    const raw = env[key];
    if (raw === undefined || raw === "") return fallback;
    const parsed = Number(raw);
    if (!Number.isFinite(parsed)) throw new Error(`${key}는 숫자여야 합니다: ${raw}`);
    return parsed;
  };
  return {
    lat: num("CENTER_LAT", ORDER_ZONE.lat),
    lng: num("CENTER_LNG", ORDER_ZONE.lng),
    spread: num("SPREAD_DEG", 0.01),
    mode: (env.DIST_MODE ?? "national").toLowerCase(),
  };
}

/** 배치 결과를 한 줄로 요약한다. 실행 로그와 agents.json에 남겨 런 조건을 사후에 대조할 수 있게 한다. */
export function describePlacement(agents) {
  const byZone = new Map();
  for (const a of agents) {
    const key = a.zone ?? a.order?.zone ?? "?";
    byZone.set(key, (byZone.get(key) ?? 0) + 1);
  }
  return [...byZone].map(([zone, count]) => `${zone} ${count}`).join(" · ");
}

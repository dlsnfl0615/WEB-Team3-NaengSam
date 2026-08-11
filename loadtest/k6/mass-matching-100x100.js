import http from "k6/http";
import { check, sleep } from "k6";
import { Trend, Rate, Counter } from "k6/metrics";

// PLAN.md ⑤: 부르미 100명 vs 드리미 100명 동시 매칭 통합 시나리오.
//
// 계정: backend/sql/test-seed-accounts.sql 이 boormi1..100@test.test / dreami1..100@test.test
// (전부 승인된 드리미 포함, 비밀번호 string)를 시드해두므로 이 스크립트는 그 계정을 그대로 쓴다.
// 좌표는 시드에 넣을 필요 없음 — 이 스크립트가 온라인 등록/콜 등록 시점에 직접 흩뿌린다.
//
// 선행 조건(PLAN.md 미해결 사항 참고): 카카오 API가 목으로 전환되어 있을 것 — 콜 100건 = 실호출
// 300회라 그대로 돌리면 위험하다. 목 전환 전엔 -e BOORMI_COUNT=/-e DREAMI_COUNT= 를 작게 줘서
// (예: 3) 로직만 먼저 검증할 것.
//
// 구조상 이유로 "VU 100명이 각자 독립적으로" 가 아니라 단일 흐름(iterations=1) 안에서
// http.batch()로 실제 동시 요청을 낸다: k6는 VU마다 독립된 JS 힙이라, "이 드리미가 어떤 오퍼를
// 받았는지"를 여러 VU에 걸쳐 실시간으로 공유할 방법이 없다. loadtest/concurrent-accept-race.sh와
// 같은 접근을 100:100 규모로 올린 것이라고 보면 된다 — 오퍼 탐색은 디버그 API
// (GET /api/v1/debug/matching/orders/{orderId}/group)로 하고, 실제 수락은 프로덕션 API
// (POST /api/v1/dreami/offers/{offerId}/accept)로 해당 드리미 본인 세션을 통해 수행한다.
//
// 매칭엔진이 라운드당 최대 3명(MatchingService.MAX_OFFER_COUNT)에게만 오퍼를 보내므로,
// 100건이 한 번에 다 매칭되지 않고 여러 라운드에 걸쳐 점진적으로 풀린다. 그래서 "즉시 매칭"이
// 아니라 "MATCH_TIMEOUT_S 안에 몇 건이 드리미 수락까지 도달했는지"를 지표로 삼는다(부르미 확정까지는
// 범위 밖 — concurrent-accept-race.sh와 동일하게 수락 단계까지만 본다).
//
// 실행 예:
//   k6 run -e BOORMI_COUNT=3 -e DREAMI_COUNT=3 loadtest/k6/mass-matching-100x100.js   # 로직 검증용 소규모
//   k6 run -e BOORMI_COUNT=100 -e DREAMI_COUNT=100 loadtest/k6/mass-matching-100x100.js
//
// 주의 — 재실행 전 DB 리셋 필요: 이 스크립트는 부르미 확정까지 안 가므로(수락 단계까지만) 만든 주문이
// MATCHING 또는 PENDING_BOORMI_CONFIRMATION 상태로 그대로 남는다. 재실행하면 (1) 부르미는
// MAX_ACTIVE_ORDERS(=5) 캡에 걸려 ORDER_009로 콜 등록이 거부되고 (2) 수락까지 갔던 드리미는
// ALREADY_HAS_ACTIVE_ORDER로 온라인 등록 자체가 실패한다. 다시 돌리기 전에
// backend/sql/test-seed-accounts.sql을 재실행해 데이터를 리셋할 것.
const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";
const PASSWORD = __ENV.TEST_PASSWORD || "string";
const BOORMI_COUNT = Number(__ENV.BOORMI_COUNT || 100);
const DREAMI_COUNT = Number(__ENV.DREAMI_COUNT || 100);
const BOORMI_EMAIL_PREFIX = __ENV.BOORMI_EMAIL_PREFIX || "boormi";
const DREAMI_EMAIL_PREFIX = __ENV.DREAMI_EMAIL_PREFIX || "dreami";
const EMAIL_DOMAIN = __ENV.EMAIL_DOMAIN || "test.test";
const MATCH_POLL_INTERVAL_S = Number(__ENV.MATCH_POLL_INTERVAL_S || 2);
const MATCH_TIMEOUT_S = Number(__ENV.MATCH_TIMEOUT_S || 60);

// 강남역 인근 반경(m) 안에서 흩뿌린다 — 시드에 좌표를 미리 넣지 않아도 되게 하려고 여기서 분산시킨다.
const CENTER_LAT = Number(__ENV.CENTER_LAT || 37.4979);
const CENTER_LNG = Number(__ENV.CENTER_LNG || 127.0276);
const SPREAD_RADIUS_M = Number(__ENV.SPREAD_RADIUS_M || 2000);

export const options = {
  // 100:100 규모에서 setup()이 로그인 200회(+/me 100회) — 총 300번의 순차 HTTP 호출을 하므로
  // 기본 60초로는 부족할 수 있다. 로그인은 PBKDF2 210,000회 반복이라 서버 쪽도 느리다.
  setupTimeout: "5m",
  scenarios: {
    massMatching: {
      executor: "shared-iterations",
      vus: 1,
      iterations: 1,
      maxDuration: `${MATCH_TIMEOUT_S + 60}s`,
    },
  },
};

const orderCreateLatency = new Trend("order_create_latency");
const acceptedCount = new Counter("accepted_offers");
const acceptedWithinTimeoutRate = new Rate("accepted_within_timeout_rate");

// Set-Cookie 헤더의 Secure 플래그는 k6 자동 쿠키자가 http:// 요청엔 다시 실어주지 않으므로 직접 뽑아 붙인다.
function extractSessionCookie(res) {
  const raw = res.headers["Set-Cookie"];
  if (!raw) return null;
  const match = raw.match(/JSESSIONID=[^;]+/);
  return match ? match[0] : null;
}

function login(email) {
  const res = http.post(
    `${BASE_URL}/api/v1/user/login`,
    JSON.stringify({ email, password: PASSWORD }),
    { headers: { "Content-Type": "application/json" }, tags: { name: "login" } },
  );
  if (res.status !== 200) {
    throw new Error(`로그인 실패 (email=${email}, status=${res.status}, body=${res.body}) — 계정이 시드돼 있는지 확인하세요.`);
  }
  const cookie = extractSessionCookie(res);
  if (!cookie) throw new Error(`로그인 응답에 Set-Cookie(JSESSIONID)가 없습니다 (email=${email})`);
  return cookie;
}

function fetchBoormiId(cookie) {
  const res = http.get(`${BASE_URL}/api/v1/user/me`, { headers: { Cookie: cookie }, tags: { name: "me" } });
  return JSON.parse(res.body).result.boormiId;
}

// 반경 내 무작위 좌표 — 정밀 측지 계산은 필요 없고 흩뿌리는 목적이라 근사 변환으로 충분하다.
function randomPointNear(lat, lng, radiusM) {
  const r = radiusM * Math.sqrt(Math.random());
  const theta = Math.random() * 2 * Math.PI;
  const dLat = (r * Math.cos(theta)) / 111_320;
  const dLng = (r * Math.sin(theta)) / (111_320 * Math.cos((lat * Math.PI) / 180));
  return { latitude: lat + dLat, longitude: lng + dLng };
}

export function setup() {
  const boormiEmails = Array.from(
    { length: BOORMI_COUNT }, (_, i) => `${BOORMI_EMAIL_PREFIX}${i + 1}@${EMAIL_DOMAIN}`);
  const dreamiEmails = Array.from(
    { length: DREAMI_COUNT }, (_, i) => `${DREAMI_EMAIL_PREFIX}${i + 1}@${EMAIL_DOMAIN}`);

  const boormis = boormiEmails.map((email) => ({ email, cookie: login(email) }));
  const dreamis = dreamiEmails.map((email) => {
    const cookie = login(email);
    return { email, cookie, dreamiId: fetchBoormiId(cookie) };
  });

  // 드리미 전원 온라인 등록 — 매칭엔진이 첫 라운드부터 후보로 보게 하려고 콜 등록보다 먼저 한다.
  const onlineResponses = http.batch(
    dreamis.map((d) => ({
      method: "POST",
      url: `${BASE_URL}/api/v1/dreami/status/online`,
      body: JSON.stringify(randomPointNear(CENTER_LAT, CENTER_LNG, SPREAD_RADIUS_M)),
      params: { headers: { "Content-Type": "application/json", Cookie: d.cookie }, tags: { name: "goOnline" } },
    })),
  );
  onlineResponses.forEach((res, i) => {
    check(res, { [`드리미 온라인 등록 200 (${dreamis[i].email})`]: (r) => r.status === 200 });
  });

  // 온라인 등록은 매칭엔진 큐를 통해 비동기로 반영되므로, 실제로 다 반영됐는지 확인하고 넘어간다.
  for (let i = 0; i < 20; i++) {
    const countRes = http.get(`${BASE_URL}/api/v1/debug/matching/dreamis`, { tags: { name: "waitingDreamiCount" } });
    const count = (JSON.parse(countRes.body) || []).length;
    if (count >= DREAMI_COUNT) break;
    sleep(0.3);
  }

  return { boormis, dreamis };
}

export default function (data) {
  const { boormis, dreamis } = data;
  const dreamiById = new Map(dreamis.map((d) => [d.dreamiId, d]));

  // 1) 부르미 전원이 동시에 콜 하나씩 등록.
  const createResponses = http.batch(
    boormis.map((b) => ({
      method: "POST",
      url: `${BASE_URL}/api/v1/boormi/calls`,
      body: JSON.stringify({
        originAddressLine1: `서울 강남구 테헤란로 ${100 + Math.floor(Math.random() * 400)}`,
        destinationAddressLine1: `서울 강남구 역삼로 ${100 + Math.floor(Math.random() * 400)}`,
        itemName: "서류봉투",
        itemCd: "DOCUMENT",
      }),
      params: {
        headers: { "Content-Type": "application/json", Cookie: b.cookie },
        tags: { name: "subscribeOrder" },
      },
    })),
  );

  const orders = [];
  createResponses.forEach((res, i) => {
    orderCreateLatency.add(res.timings.duration);
    const ok = check(res, { [`콜 등록 200 (${boormis[i].email})`]: (r) => r.status === 200 });
    if (!ok) return;
    orders.push({ orderId: JSON.parse(res.body).result, boormiEmail: boormis[i].email, accepted: false });
  });

  // 2) 매칭 라운드가 여러 번 돌 것을 감안해 폴링하며, OFFERED 상태 오퍼가 보이면
  //    해당 드리미 세션으로 즉시 수락한다.
  const deadline = Date.now() + MATCH_TIMEOUT_S * 1000;
  while (Date.now() < deadline && orders.some((o) => !o.accepted)) {
    const pending = orders.filter((o) => !o.accepted);
    const groupResponses = http.batch(
      pending.map((o) => ({
        method: "GET",
        url: `${BASE_URL}/api/v1/debug/matching/orders/${o.orderId}/group`,
        params: { tags: { name: "orderOfferGroup" } },
      })),
    );

    const acceptTargets = [];
    groupResponses.forEach((res, i) => {
      if (res.status !== 200) return;
      const group = JSON.parse(res.body);
      const offer = (group.offers || []).find((o) => o.status === "OFFERED");
      if (!offer) return;
      const dreami = dreamiById.get(offer.dreamiId);
      if (!dreami) return;
      acceptTargets.push({ order: pending[i], offer, dreami });
    });

    if (acceptTargets.length > 0) {
      const acceptResponses = http.batch(
        acceptTargets.map(({ offer, dreami }) => ({
          method: "POST",
          url: `${BASE_URL}/api/v1/dreami/offers/${offer.offerId}/accept`,
          params: { headers: { Cookie: dreami.cookie }, tags: { name: "acceptOffer" } },
        })),
      );
      acceptResponses.forEach((res, i) => {
        if (res.status !== 200) return;
        acceptTargets[i].order.accepted = true;
        acceptedCount.add(1);
        acceptedWithinTimeoutRate.add(1);
      });
    }

    sleep(MATCH_POLL_INTERVAL_S);
  }

  orders.filter((o) => !o.accepted).forEach(() => acceptedWithinTimeoutRate.add(0));

  const accepted = orders.filter((o) => o.accepted).length;
  check(null, {
    "콜 등록이 최소 1건 이상 성공함": () => orders.length > 0,
    [`${MATCH_TIMEOUT_S}초 내 전부 드리미 수락 완료`]: () => orders.length > 0 && accepted === orders.length,
  });
  console.log(`${accepted}/${orders.length}건 ${MATCH_TIMEOUT_S}초 내 드리미 수락 완료 (콜 등록 시도 ${boormis.length}건)`);
}

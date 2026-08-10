import http from "k6/http";
import { check, sleep } from "k6";
import { Trend } from "k6/metrics";

// 실행 예:
//   k6 run loadtest/k6/matching-nearby-calls.js
//   k6 run -e VUS=50 -e DURATION=2m -e BASE_URL=http://localhost:8080 loadtest/k6/matching-nearby-calls.js
const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";
const EMAIL = __ENV.TEST_EMAIL || "dreami1@test.test";
const PASSWORD = __ENV.TEST_PASSWORD || "test1234!";

// 시드 데이터(backend/sql/test-seed-accounts.sql)의 주문 좌표(강남역 인근)에 맞춤 — 필요하면 -e LAT= -e LNG= 로 덮어쓴다.
const LAT = Number(__ENV.LAT || 37.4979);
const LNG = Number(__ENV.LNG || 127.0276);
const RADIUS = Number(__ENV.RADIUS || 3000);
const COUNT = Number(__ENV.COUNT || 10);

export const options = {
  scenarios: {
    polling: {
      executor: "constant-vus",
      vus: Number(__ENV.VUS || 10),
      duration: __ENV.DURATION || "1m",
    },
  },
  thresholds: {
    http_req_duration: ["p(95)<500"],
    http_req_failed: ["rate<0.01"],
  },
};

// Set-Cookie 헤더는 "JSESSIONID=...; SameSite=None; Secure; Path=/" 형태로 온다.
// k6의 자동 쿠키자는 Secure 플래그가 붙은 쿠키를 http:// 요청엔 다시 실어주지 않으므로
// (로컬 테스트는 대개 http://localhost), 로그인 응답에서 값만 뽑아 직접 Cookie 헤더로 붙인다.
function extractSessionCookie(res) {
  const raw = res.headers["Set-Cookie"];
  if (!raw) return null;
  const match = raw.match(/JSESSIONID=[^;]+/);
  return match ? match[0] : null;
}

// 응답에 담긴 콜 개수 추이 — 0건만 계속 나오면 사전에 열어둔 콜이 없거나 반경 밖이라는 뜻이다.
const nearbyCallCount = new Trend("nearby_call_count");

export function setup() {
  const res = http.post(
    `${BASE_URL}/api/v1/user/login`,
    JSON.stringify({ email: EMAIL, password: PASSWORD }),
    { headers: { "Content-Type": "application/json" } },
  );

  const ok = check(res, {
    "로그인 200 응답": (r) => r.status === 200,
  });
  if (!ok) {
    throw new Error(`로그인 실패 (status=${res.status}, body=${res.body}) — 계정/서버 상태를 확인하세요.`);
  }

  const cookie = extractSessionCookie(res);
  if (!cookie) {
    throw new Error("로그인 응답에 Set-Cookie(JSESSIONID)가 없습니다.");
  }

  return { cookie };
}

export default function (data) {
  const res = http.post(
    `${BASE_URL}/api/v1/dreami/calls/nearby`,
    JSON.stringify({ lat: LAT, lng: LNG, radius: RADIUS, count: COUNT }),
    {
      headers: {
        "Content-Type": "application/json",
        Cookie: data.cookie,
      },
    },
  );

  check(res, {
    "200 응답": (r) => r.status === 200,
    "isSuccess": (r) => {
      try {
        return JSON.parse(r.body).isSuccess === true;
      } catch {
        return false;
      }
    },
  });

  try {
    const body = JSON.parse(res.body);
    nearbyCallCount.add(Array.isArray(body.result) ? body.result.length : 0);
  } catch {
    nearbyCallCount.add(0);
  }

  // 프론트 MatchingScreen이 실제로 5초 간격으로 폴링하는 것과 동일하게 맞춘다.
  sleep(5);
}

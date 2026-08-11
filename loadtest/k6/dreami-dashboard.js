import http from "k6/http";
import { check } from "k6";

// PLAN.md ③: 순수 DB 조회 부하테스트 — GET /api/v1/dreami/dashboard.
// 카카오 의존이 없어서 반복 실행 걱정 없이, 정산(MONEY_TX)/완료 배달 건수를 SQL로 늘려가며
// 집계 쿼리(월별 정산 합계 등)가 커지면 느려지는지 실험하기 좋은 대상.
// 세션 하나(dreami1@test.test — 시드에 완료 배달 10건 + 정산 66,500원 있어 대시보드 필드가 다 채워짐)를
// setup()에서 만들어 재사용한다.
//
// 실행 예:
//   k6 run loadtest/k6/dreami-dashboard.js
//   k6 run -e VUS=50 -e DURATION=1m loadtest/k6/dreami-dashboard.js
const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";
const EMAIL = __ENV.TEST_EMAIL || "dreami1@test.test";
const PASSWORD = __ENV.TEST_PASSWORD || "string";

export const options = {
  scenarios: {
    reading: {
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

// Set-Cookie 헤더의 Secure 플래그는 k6 자동 쿠키자가 http:// 요청엔 다시 실어주지 않으므로 직접 뽑아 붙인다.
function extractSessionCookie(res) {
  const raw = res.headers["Set-Cookie"];
  if (!raw) return null;
  const match = raw.match(/JSESSIONID=[^;]+/);
  return match ? match[0] : null;
}

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
  const res = http.get(`${BASE_URL}/api/v1/dreami/dashboard`, {
    headers: { Cookie: data.cookie },
  });

  check(res, {
    "200 응답": (r) => r.status === 200,
    "isSuccess": (r) => {
      try {
        return JSON.parse(r.body).isSuccess === true;
      } catch {
        return false;
      }
    },
    "completedCount 존재": (r) => {
      try {
        return typeof JSON.parse(r.body).result?.completedCount === "number";
      } catch {
        return false;
      }
    },
  });
}

import http from "k6/http";
import { check } from "k6";

// PLAN.md ③: 순수 DB 조회 부하테스트 — GET /api/v1/boormi/calls.
// 카카오 의존이 없어서 반복 실행 걱정 없이, 시드 데이터량을 SQL로 늘려가며(주문 건수 증가)
// 커서 페이지네이션 조회가 커지면 느려지는지 실험하기 좋은 대상.
// 로그인은 신원별로 결과가 다르지만(본인 주문만 보임), 순수 읽기 처리량 실험 목적이므로
// 세션 하나(boormi1@test.test — 시드에 완료 주문 2건 있음)를 setup()에서 만들어 재사용한다.
//
// 실행 예:
//   k6 run loadtest/k6/boormi-calls-list.js
//   k6 run -e VUS=50 -e DURATION=1m -e STATUS=COMPLETED loadtest/k6/boormi-calls-list.js
const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";
const EMAIL = __ENV.TEST_EMAIL || "boormi1@test.test";
const PASSWORD = __ENV.TEST_PASSWORD || "string";
const SIZE = Number(__ENV.SIZE || 20);
const STATUS = __ENV.STATUS || "";

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
  const query = STATUS ? `?size=${SIZE}&status=${STATUS}` : `?size=${SIZE}`;
  const res = http.get(`${BASE_URL}/api/v1/boormi/calls${query}`, {
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
    "orders 배열 존재": (r) => {
      try {
        return Array.isArray(JSON.parse(r.body).result?.orders);
      } catch {
        return false;
      }
    },
  });
}

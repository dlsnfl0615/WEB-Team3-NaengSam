import http from "k6/http";
import { check } from "k6";

// PLAN.md ②: 로그인 POST /api/v1/user/login 단독 부하테스트.
// PasswordHasher가 PBKDF2WithHmacSHA256 210,000 iteration을 매 요청마다 돌리므로,
// 로그인 자체의 CPU 비용이 동시 사용자 수가 늘어날 때 처리량을 얼마나 깎아먹는지 본다.
// 계정 신원 자체는 결과에 영향이 없으므로(모두 findByEmail + PBKDF2 비교만) 시드 계정
// 12개(backend/sql/test-seed-accounts.sql + test-seed-dreami-extra.sql)를 VU끼리 나눠 쓴다.
// 로그인은 동시 요청에도 계정당 락/유니크제약이 없어 세션이 각자 새로 발급된다(세션 정리는 안 함 — 짧은 실행 기준).
//
// 실행 예:
//   k6 run loadtest/k6/login.js
//   k6 run -e VUS=50 -e DURATION=1m -e BASE_URL=http://localhost:8080 loadtest/k6/login.js
const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";
const PASSWORD = __ENV.TEST_PASSWORD || "string";

const ACCOUNTS = [
  "boormi1@test.test", "boormi2@test.test",
  "dreami1@test.test", "dreami2@test.test", "dreami3@test.test", "dreami4@test.test",
  "dreami5@test.test", "dreami6@test.test", "dreami7@test.test", "dreami8@test.test",
  "dreami9@test.test", "dreami10@test.test",
];

export const options = {
  scenarios: {
    login: {
      executor: "constant-vus",
      vus: Number(__ENV.VUS || 10),
      duration: __ENV.DURATION || "1m",
    },
  },
  thresholds: {
    http_req_duration: ["p(95)<1000"],
    http_req_failed: ["rate<0.01"],
  },
};

export default function () {
  const email = ACCOUNTS[__VU % ACCOUNTS.length];
  const res = http.post(
    `${BASE_URL}/api/v1/user/login`,
    JSON.stringify({ email, password: PASSWORD }),
    { headers: { "Content-Type": "application/json" } },
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
    "JSESSIONID 발급": (r) => Boolean(r.headers["Set-Cookie"] && r.headers["Set-Cookie"].includes("JSESSIONID")),
  });
}

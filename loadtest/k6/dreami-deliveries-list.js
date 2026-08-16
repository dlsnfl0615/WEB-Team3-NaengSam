import http from "k6/http";
import { check } from "k6";

// PLAN.md ③: 순수 DB 조회 부하테스트 — GET /api/v1/dreami/deliveries (드리미 활동 내역, 커서 페이지네이션).
// dreami-dashboard.js(순수 집계 쿼리)와 달리 이 엔드포인트가 실제로 커서 페이지네이션
// (OrderRepository.findFirstPageByRole / findPageByRoleAfterCursor)을 타는 대상이다.
// 첫 페이지만 반복 조회하면 커서 이후 조회(findPageByRoleAfterCursor) 경로가 전혀 부하를
// 안 받으므로, 매 iteration마다 nextCursor를 따라 MAX_PAGES까지 페이지를 이어서 넘기고
// 끝에 도달하면(hasNext=false) 다시 첫 페이지로 되돌아간다 — 얕은 페이지/깊은 페이지 조회가
// 골고루 섞여서 나가게 하기 위함.
//
// 세션 하나(dreami1@test.test)를 setup()에서 만들어 재사용한다 — 이 엔드포인트는 dreami_id로
// 본인 것만 걸러서 보여주는 신원 의존 API지만, 지금 목적은 "여러 사용자 동시성"이 아니라
// "한 계정의 활동 내역이 아주 많을 때 쿼리가 느려지는지"라서 계정을 고정하는 게 맞다.
// dreami1은 backend/sql/loadtest-seed-dreami1-deliveries.sql로 활동 내역을 대량으로 채워둔 계정.
//
// 실행 예:
//   k6 run loadtest/k6/dreami-deliveries-list.js
//   k6 run -e VUS=50 -e DURATION=1m -e SIZE=20 -e MAX_PAGES=30 -e STATUS=COMPLETED loadtest/k6/dreami-deliveries-list.js
const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";
const EMAIL = __ENV.TEST_EMAIL || "dreami1@test.test";
const PASSWORD = __ENV.TEST_PASSWORD || "string";
const SIZE = Number(__ENV.SIZE || 20);
const STATUS = __ENV.STATUS || "";
// 한 iteration에서 첫 페이지 포함 최대 몇 페이지까지 커서를 따라갈지. 시드 건수 / SIZE 보다
// 크게 잡아두면 매 iteration이 리스트 끝까지 갔다가 처음으로 되돌아가는 것으로 수렴한다.
const MAX_PAGES = Number(__ENV.MAX_PAGES || 30);

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

function fetchPage(cookie, cursor) {
  const params = [`size=${SIZE}`];
  if (STATUS) params.push(`status=${STATUS}`);
  if (cursor) params.push(`cursor=${encodeURIComponent(cursor)}`);

  const res = http.get(`${BASE_URL}/api/v1/dreami/deliveries?${params.join("&")}`, {
    headers: { Cookie: cookie },
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

  try {
    return JSON.parse(res.body).result;
  } catch {
    return null;
  }
}

export default function (data) {
  let cursor = undefined;

  for (let page = 0; page < MAX_PAGES; page++) {
    const result = fetchPage(data.cookie, cursor);
    if (!result || !result.hasNext) break;
    cursor = result.nextCursor;
  }
}

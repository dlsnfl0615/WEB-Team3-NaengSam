import http from "k6/http";
import { check, sleep } from "k6";

// PLAN.md ③: 순수 DB 조회 부하테스트 — GET /api/v1/dreami/deliveries, 다계정 버전.
//
// dreami-deliveries-list.js는 계정 하나(dreami1)를 모든 VU가 세션 하나로 공유해서 "이
// 쿼리 자체가 동시 요청 앞에서 버티는지"를 봤다. 근데 그 결과만으로는 "동시 요청이 느린 게
// 쿼리 비용 때문인지, 아니면 같은 세션에 대한 서버 쪽 내부 직렬화 때문인지" 구분이 안 된다.
// 이 스크립트는 VU마다 서로 다른 계정으로 각자 로그인해서, "데이터 많은 계정 여러 개가
// 동시에 조회될 때 시스템 전체가 버티는지"를 본다 — 세션이 겹치지 않으니 위 구분이 가능해진다.
//
// 대상 계정: dreami(ACCOUNT_START) ~ dreami(ACCOUNT_START+ACCOUNT_COUNT-1), 기본 dreami400~419.
// 각 계정은 backend/sql/loadtest-seed-multi-dreami-deliveries.sql로 미리 대량의 활동 내역을
// 채워둬야 한다(기본 계정당 5000건).
//
// 이 범위는 다른 loadtest 스크립트(login.js의 dreami1~10, dreami-dashboard.js/
// matching-nearby-calls.js의 dreami1, mass-matching-100x100.js/subscribe-order.js의
// boormi1~100)와 겹치지 않게 골랐다. 겹치면 그 계정으로 다른 스크립트가 로그인할 때마다
// "한 디바이스만" 세션 정책 때문에 이 스크립트가 캐싱해둔 세션이 무효화되어 401이 대량
// 발생한다(실제로 dreami2~21을 썼다가 겪은 문제 — login.js의 dreami1~10과 겹쳤었음).
//
// VU 수가 ACCOUNT_COUNT보다 많으면 계정을 순환해서 재사용한다(예: ACCOUNT_COUNT=20인데
// VUS=50이면 21번째 VU는 다시 dreami400을 씀 — 완전히 새 계정을 뜻하는 게 아니라 "동시에
// 이 계정에 접속하는 세션이 여러 개"가 될 수 있다는 점 참고).
//
// k6 setup()은 전체 테스트에 한 번만 실행되어 VU별로 다른 계정을 로그인시킬 수 없으므로,
// 로그인은 default() 안에서 각 VU가 처음 실행될 때 한 번만 하고 모듈 스코프 변수에 캐싱한다
// (매 iteration마다 로그인하면 PBKDF2 비용이 섞여 순수 조회 성능 측정이 아니게 된다).
//
// 실행 예:
//   k6 run loadtest/k6/dreami-deliveries-list-multi-account.js
//   k6 run -e VUS=20 -e DURATION=5m -e ACCOUNT_COUNT=20 -e ACCOUNT_START=400 loadtest/k6/dreami-deliveries-list-multi-account.js
// const BASE_URL = __ENV.BASE_URL || "https://d3cev4xst074qp.cloudfront.net";
const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";
const PASSWORD = __ENV.TEST_PASSWORD || "string";
const SIZE = Number(__ENV.SIZE || 20);
const STATUS = __ENV.STATUS || "";
const MAX_PAGES = Number(__ENV.MAX_PAGES || 30);
// loadtest-seed-multi-dreami-deliveries.sql의 @dreami_account_count와 맞춰야 한다.
const ACCOUNT_COUNT = Number(__ENV.ACCOUNT_COUNT || 20);
// 다른 loadtest 스크립트와 안 겹치는 대역(400)을 기본값으로 둔다. 위 주석 참고.
// loadtest-seed-multi-dreami-deliveries.sql의 @dreami_account_start와 맞춰야 한다.
const ACCOUNT_START = Number(__ENV.ACCOUNT_START || 400);

export const options = {
  scenarios: {
    reading: {
      executor: "constant-vus",
      vus: Number(__ENV.VUS || 20),
      duration: __ENV.DURATION || "5m",
    },
  },
  thresholds: {
    http_req_failed: ["rate<0.01"],
    // 로그인(POST /login)과 활동 내역 조회(GET /deliveries)를 태그로 분리해서 집계한다.
    // VU당 로그인은 딱 한 번뿐이라 건수는 적지만, 로그인 자체가 원래 느릴 수 있어서(PBKDF2)
    // 같이 묶으면 조회 쿼리의 순수 지연시간(p95 등)이 왜곡된다. 조회 쪽에만 임계치를 건다.
    "http_req_duration{name:deliveries}": ["p(95)<500"],
  },
};

// Set-Cookie 헤더의 Secure 플래그는 k6 자동 쿠키자가 http:// 요청엔 다시 실어주지 않으므로 직접 뽑아 붙인다.
function extractSessionCookie(res) {
  const raw = res.headers["Set-Cookie"];
  if (!raw) return null;
  const match = raw.match(/JSESSIONID=[^;]+/);
  return match ? match[0] : null;
}

// VU마다 독립된 모듈 인스턴스라서, 이 변수는 사실상 "이 VU 전용" 캐시로 동작한다.
let cachedCookie = null;

// 이 VU가 처음 호출될 때만 로그인하고, 이후 iteration에서는 캐싱된 쿠키를 재사용한다.
function loginOnceForThisVU() {
  if (cachedCookie) return cachedCookie;

  // __VU는 1부터 시작하는 VU 번호. ACCOUNT_COUNT로 나눠 계정을 순환 배정한다.
  const accountIndex = ACCOUNT_START + ((__VU - 1) % ACCOUNT_COUNT);
  const email = `dreami${accountIndex}@test.test`;

  const res = http.post(
    `${BASE_URL}/api/v1/user/login`,
    JSON.stringify({ email, password: PASSWORD }),
    { headers: { "Content-Type": "application/json" }, tags: { name: "login" } },
  );

  const ok = check(res, {
    "로그인 200 응답": (r) => r.status === 200,
  });
  if (!ok) {
    throw new Error(
      `로그인 실패 (email=${email}, status=${res.status}, body=${res.body}) — 계정/서버 상태를 확인하세요.`,
    );
  }

  const cookie = extractSessionCookie(res);
  if (!cookie) {
    throw new Error(`로그인 응답에 Set-Cookie(JSESSIONID)가 없습니다 (email=${email}).`);
  }

  cachedCookie = cookie;
  return cachedCookie;
}

function fetchPage(cookie, cursor) {
  const params = [`size=${SIZE}`];
  if (STATUS) params.push(`status=${STATUS}`);
  if (cursor) params.push(`cursor=${encodeURIComponent(cursor)}`);

  const res = http.get(`${BASE_URL}/api/v1/dreami/deliveries?${params.join("&")}`, {
    headers: { Cookie: cookie },
    tags: { name: "deliveries" },
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

export default function () {
  // loginOnceForThisVU가 실패하면(대기열 만석 등) 아래 for 루프까지 가지도 못하고 여기서 바로
  // 예외가 던져진다 — 그 상태로 함수를 빠져나가면 맨 아래 sleep(1)을 못 타서, 로그인 실패가
  // 페이지 조회 실패와 달리 pacing 없이 즉시 재시도되며 동일한 폭주 되먹임을 일으킨다. 그래서
  // 로그인 실패도 페이지 조회 실패와 똑같이 sleep(1)을 타고 이번 iteration을 끝내도록 감싼다.
  let cookie;
  try {
    cookie = loginOnceForThisVU();
  } catch (e) {
    console.error(e.message);
    sleep(1);
    return;
  }

  let cursor = undefined;

  for (let page = 0; page < MAX_PAGES; page++) {
    const result = fetchPage(cookie, cursor);
    if (!result || !result.hasNext) break;
    cursor = result.nextCursor;
  }

  // 페이지 요청이 실패하면 위 루프가 1페이지 만에 break되는데, constant-vus는 iteration이 끝나자마자
  // 같은 VU에 새 iteration을 바로 또 돌린다. sleep 없이는 "실패 → 즉시 재요청 → 서버 과부하 심화 →
  // 더 많은 실패"로 이어지는 되먹임이 생겨, VU 수보다 훨씬 큰 부하가 실려서 실제 동시 사용자 수 기준의
  // 측정이 아니게 된다(실제로 50 VU에서 iteration이 정상 대비 163배 폭증하며 재현된 문제).
  // 성공/실패와 무관하게 매 iteration 끝에 고정 pacing을 둬 이 폭주를 막는다.
  sleep(1);
}

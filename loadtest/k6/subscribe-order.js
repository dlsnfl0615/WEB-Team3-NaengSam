import http from "k6/http";
import { check } from "k6";
import { Rate } from "k6/metrics";

// PLAN.md ④: 주문 접수 부하테스트 — POST /api/v1/boormi/calls (subscribeOrder).
//
// 주의 1 — 카카오 실호출: 콜 하나 등록마다 지오코딩 x2 + 길찾기 x1, 실제 카카오 API가 3회 호출된다
// (BoormiService.toGeoPoint/getRoute). 목 처리가 준비되기 전까지는 VUS/DURATION을 작게 잡아
// 반복 실행 비용을 최소화할 것. 목 전환 후에는 -e 로 그대로 올려서 쓰면 된다(스크립트는 동일).
//
// 주의 2 — 계정별 진행 중 요청 5건 캡: BoormiService.MAX_ACTIVE_ORDERS(=5)를 넘으면 ORDER_009로
// 거부된다. 이 스크립트는 각 iteration에서 콜 생성 직후 바로 취소(DELETE)해서 활성 주문 수를
// 늘리지 않지만, 동시에 여러 VU가 같은 계정을 쓰면 취소가 반영되기 전에 여러 건이 겹쳐 캡에
// 걸릴 수 있다 — VUS를 계정 수보다 많이 올리면 이 캡 자체가 병목처럼 보일 수 있음
// (order009Rate 지표로 서버 성능과 구분한다). 기본값은 시드 boormi1~20을 나눠 쓴다.
//
// 실행 예:
//   k6 run loadtest/k6/subscribe-order.js
//   k6 run -e VUS=2 -e DURATION=30s -e BASE_URL=http://localhost:8080 loadtest/k6/subscribe-order.js
const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";
const PASSWORD = __ENV.TEST_PASSWORD || "string";
// backend/sql/test-seed-accounts.sql이 boormi1..100@test.test를 시드한다. VUS를 늘리면
// -e ACCOUNTS=boormi1@test.test,...,boormi100@test.test 로 필요한 만큼 넓혀 쓰면 된다.
const ACCOUNTS = (__ENV.ACCOUNTS || Array.from({ length: 20 }, (_, i) => `boormi${i + 1}@test.test`).join(",")).split(",");

export const options = {
  scenarios: {
    subscribing: {
      executor: "constant-vus",
      vus: Number(__ENV.VUS || 2),
      duration: __ENV.DURATION || "30s",
    },
  },
  thresholds: {
    "http_req_duration{name:subscribeOrder}": ["p(95)<3000"], // 카카오 실호출 3회 포함이라 여유를 둠
    "http_req_failed{name:subscribeOrder}": ["rate<0.01"],
    order009Rate: ["rate<0.05"], // 계정 캡(ORDER_009)으로 실패한 비율 — 서버 성능과는 별개 지표
  },
};

// 서버가 실제로 처리하는 주문 접수 자체(카카오 호출 포함)만 재는 지표 — ORDER_009로 즉시 거부된 건 제외.
const order009Rate = new Rate("order009Rate");

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
    { headers: { "Content-Type": "application/json" } },
  );
  const ok = check(res, { "로그인 200 응답": (r) => r.status === 200 });
  if (!ok) {
    throw new Error(`로그인 실패 (email=${email}, status=${res.status}, body=${res.body})`);
  }
  const cookie = extractSessionCookie(res);
  if (!cookie) {
    throw new Error(`로그인 응답에 Set-Cookie(JSESSIONID)가 없습니다 (email=${email})`);
  }
  return cookie;
}

// setup()은 한 번만 실행되므로, 계정마다 로그인해서 VU가 나눠 쓸 쿠키 목록을 만들어둔다.
export function setup() {
  return { cookies: ACCOUNTS.map(login) };
}

function orderBody() {
  return JSON.stringify({
    originAddressLine1: "서울 강남구 테헤란로 152",
    destinationAddressLine1: "서울 강남구 역삼로 180",
    itemName: "서류봉투",
    itemCd: "DOCUMENT",
  });
}

export default function (data) {
  const cookie = data.cookies[__VU % data.cookies.length];
  const headers = { "Content-Type": "application/json", Cookie: cookie };

  const createRes = http.post(`${BASE_URL}/api/v1/boormi/calls`, orderBody(), {
    headers,
    tags: { name: "subscribeOrder" },
  });

  let body = null;
  try {
    body = JSON.parse(createRes.body);
  } catch {
    // no-op — 아래 check에서 실패로 잡힘
  }

  const isOrder009 = createRes.status === 409 && body?.code === "ORDER_009";
  order009Rate.add(isOrder009);

  const created = check(createRes, {
    "200 응답 (또는 ORDER_009 캡)": (r) => r.status === 200 || isOrder009,
    "orderId 발급": () => !isOrder009 && typeof body?.result === "string" && body.result.length > 0,
  });

  if (!created || isOrder009) return;

  const orderId = body.result;
  const cancelRes = http.del(`${BASE_URL}/api/v1/boormi/calls/${orderId}`, null, {
    headers,
    tags: { name: "unsubscribeOrder" },
  });

  check(cancelRes, {
    "취소 200 응답": (r) => r.status === 200,
  });
}

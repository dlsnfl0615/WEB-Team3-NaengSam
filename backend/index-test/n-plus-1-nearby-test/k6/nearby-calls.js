import http from 'k6/http';
import { check, fail, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const API_BASE = __ENV.API_BASE || 'http://localhost:8080';
const TEST_EMAIL = __ENV.TEST_EMAIL || 'nearby-n-plus-one@index.test';
const TEST_PASSWORD = __ENV.TEST_PASSWORD || 'index-test';
const VUS = Number(__ENV.VUS || 1);
const DURATION = __ENV.DURATION || '2m';
const WARMUP_ITERATIONS = Number(__ENV.WARMUP_ITERATIONS || 20);
const THINK_TIME_SECONDS = Number(__ENV.THINK_TIME_SECONDS || 0);

const CENTER_LATITUDE = 37.4979;
const CENTER_LONGITUDE = 127.0276;
const TEST_BOORMI_ID = '46000000-0000-0000-0000-000000000001';

const nearbyCallsDuration = new Trend('nearby_calls_duration', true);
const nearbyCallsFailed = new Rate('nearby_calls_failed');

function orderIdFor(sequence) {
  const hex = `46000000000000000001${sequence.toString(16).padStart(12, '0')}`;
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

const TARGET_ORDER_IDS = Array.from({ length: 10 }, (_, index) => orderIdFor(index + 1));
const TARGET_ORDER_ID_SET = new Set(TARGET_ORDER_IDS);

export const options = {
  setupTimeout: '2m',
  scenarios: {
    nearby_calls: {
      executor: 'constant-vus',
      vus: VUS,
      duration: DURATION,
      gracefulStop: '10s',
    },
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max', 'count'],
  thresholds: {
    checks: ['rate>0.99'],
    nearby_calls_failed: ['rate<0.01'],
  },
};

function jsonBody(response, description) {
  try {
    return response.json();
  } catch (error) {
    fail(`${description} 응답이 JSON이 아님: status=${response.status}, body=${response.body}`);
  }
}

function sessionCookie(response) {
  const cookies = response.cookies && response.cookies.JSESSIONID;
  if (!cookies || cookies.length === 0) {
    return null;
  }
  return `JSESSIONID=${cookies[0].value}`;
}

function setupParams(extraHeaders = {}) {
  return {
    headers: {
      Accept: 'application/json',
      ...extraHeaders,
    },
    tags: { phase: 'setup' },
  };
}

function login() {
  let response = http.post(
    `${API_BASE}/api/v1/user/login`,
    JSON.stringify({ email: TEST_EMAIL, password: TEST_PASSWORD }),
    setupParams({ 'Content-Type': 'application/json' }),
  );

  let body = jsonBody(response, '로그인');
  if (response.status !== 200 || body.isSuccess !== true) {
    fail(`로그인 실패: status=${response.status}, body=${response.body}`);
  }

  let result = body.result;
  if (result && result.status === 'QUEUED') {
    const ticketId = result.ticketId;
    const deadline = Date.now() + 120000;

    while (Date.now() < deadline) {
      sleep(Math.max(Number(result.pollAfterMs || 500), 100) / 1000);
      response = http.post(
        `${API_BASE}/api/v1/user/login/queue/${ticketId}`,
        null,
        setupParams(),
      );
      body = jsonBody(response, '로그인 대기열');
      if (response.status !== 200 || body.isSuccess !== true) {
        fail(`로그인 대기열 조회 실패: status=${response.status}, body=${response.body}`);
      }

      result = body.result;
      if (result && result.status === 'SUCCESS') {
        break;
      }
    }
  }

  if (!result || result.status !== 'SUCCESS') {
    fail(`로그인 완료 시간 초과: body=${JSON.stringify(body)}`);
  }

  const cookie = sessionCookie(response);
  if (!cookie) {
    fail('로그인 성공 응답에 JSESSIONID가 없음');
  }
  return cookie;
}

function waitingOrderIds() {
  const response = http.get(
    `${API_BASE}/api/v1/debug/matching/orders/waiting`,
    setupParams(),
  );
  const body = jsonBody(response, '대기 주문 조회');
  if (response.status !== 200 || body.isSuccess !== true || !Array.isArray(body.result)) {
    fail(`대기 주문 조회 실패: status=${response.status}, body=${response.body}`);
  }
  return body.result.map((order) => order.orderId);
}

function registerTargetOrders() {
  const currentIds = waitingOrderIds();
  const unexpectedIds = currentIds.filter((orderId) => !TARGET_ORDER_ID_SET.has(orderId));
  if (unexpectedIds.length > 0) {
    fail(`매칭 엔진에 다른 주문이 남아 있음. 백엔드를 재시작해야 함: ${unexpectedIds.join(', ')}`);
  }

  const currentIdSet = new Set(currentIds);
  for (let index = 0; index < TARGET_ORDER_IDS.length; index += 1) {
    const orderId = TARGET_ORDER_IDS[index];
    if (currentIdSet.has(orderId)) {
      continue;
    }

    const response = http.post(
      `${API_BASE}/api/v1/debug/matching/orders/${orderId}/start`,
      JSON.stringify({
        boormiId: TEST_BOORMI_ID,
        destination: {
          latitude: CENTER_LATITUDE + index * 0.00001,
          longitude: CENTER_LONGITUDE + index * 0.00001,
        },
      }),
      setupParams({ 'Content-Type': 'application/json' }),
    );
    if (response.status !== 200) {
      fail(`매칭 엔진 주문 등록 실패: orderId=${orderId}, status=${response.status}, body=${response.body}`);
    }
  }

  const deadline = Date.now() + 15000;
  while (Date.now() < deadline) {
    const waitingIds = waitingOrderIds();
    if (TARGET_ORDER_IDS.every((orderId) => waitingIds.includes(orderId))) {
      return;
    }
    sleep(0.1);
  }
  fail('매칭 엔진에 대상 주문 10건이 등록되지 않음');
}

function nearbyRequest(cookie, phase) {
  return http.post(
    `${API_BASE}/api/v1/dreami/calls/nearby`,
    JSON.stringify({
      lat: CENTER_LATITUDE,
      lng: CENTER_LONGITUDE,
      radius: 5000,
      count: 10,
    }),
    {
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/json',
        Cookie: cookie,
      },
      tags: {
        name: 'POST /api/v1/dreami/calls/nearby',
        phase,
      },
    },
  );
}

function hasExpectedOrders(response) {
  let body;
  try {
    body = response.json();
  } catch (error) {
    return false;
  }

  if (response.status !== 200 || body.isSuccess !== true || !Array.isArray(body.result)) {
    return false;
  }
  const actualIds = body.result.map((call) => call.orderId);
  return actualIds.length === TARGET_ORDER_IDS.length
    && TARGET_ORDER_IDS.every((orderId) => actualIds.includes(orderId));
}

export function setup() {
  const cookie = login();
  registerTargetOrders();

  for (let index = 0; index < WARMUP_ITERATIONS; index += 1) {
    const response = nearbyRequest(cookie, 'warmup');
    if (!hasExpectedOrders(response)) {
      fail(`워밍업 요청 실패: status=${response.status}, body=${response.body}`);
    }
  }

  return { cookie };
}

export default function (data) {
  const response = nearbyRequest(data.cookie, 'measurement');
  const succeeded = hasExpectedOrders(response);

  nearbyCallsDuration.add(response.timings.duration);
  nearbyCallsFailed.add(!succeeded);
  check(response, {
    '주변 콜 10건을 정상 반환한다': () => succeeded,
  });

  if (THINK_TIME_SECONDS > 0) {
    sleep(THINK_TIME_SECONDS);
  }
}

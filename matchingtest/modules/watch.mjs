/**
 * 실클라이언트 검증기 (Playwright).
 *
 * 부하가 도는 동안 진짜 브라우저 몇 개가 같은 매칭을 타게 하고, 화면에 실제로 팝업이 뜨고
 * 배달 화면까지 넘어가는지를 단언한다. 영상만 남기던 기존 farm.mjs와 달리 통과/실패를 판정한다.
 *
 * 유저마다 BrowserContext가 아니라 Browser를 따로 띄우는 이유:
 *  1. 세션 쿠키(JSESSIONID)가 유저별로 완전히 분리돼야 한다.
 *  2. SSE는 유저당 연결을 하나씩 물고 있는데, HTTP/1.1은 origin당 동시 연결이 6개다.
 *     한 브라우저에 탭을 여럿 띄우면 7번째부터 SSE가 열리지 않고 대기한다.
 *
 * 부하보다 **먼저** 준비를 마쳐야 한다. 오퍼 배정이 거리 무관 전역 FIFO(`updatedAt` 오름차순)이므로,
 * 브라우저 드리미가 큐 앞단을 잡고 있어야 에이전트 100명 사이에서 오퍼를 받는다.
 */
import { chromium } from "playwright";
import { mkdir } from "node:fs/promises";
import { join } from "node:path";

/** 기록할 매칭 SSE 이벤트 타입. 백엔드 `MatchingEventType`의 소문자 표기와 같다. */
const SSE_TYPES = [
  "offer_popup",
  "offer_closed",
  "offer_error",
  "dreami_info",
  "delivery_started_dreami",
  "delivery_started_boormi",
];

/** 선착순에서 졌을 때만 오는 이벤트. 승자는 dreami_info → delivery_started_* 로 간다. */
const LOSS_TYPES = ["offer_closed", "offer_error"];

/** 계측된 SSE를 사람이 읽는 한 줄로. 실패 원인이 "뭘 받았나"에 달려 있어 메시지에 같이 싣는다. */
async function sseDump(page, from = 0) {
  const events = await page.evaluate((at) => window.__sse?.slice(at) ?? [], from).catch(() => []);
  return events.length === 0 ? "없음" : events.map((e) => e.type).join(" → ");
}

/**
 * 유저 한 명 = 브라우저 한 개. 로그인까지 마친 page를 돌려준다.
 */
async function openUser(user, index, config) {
  const x = (index % config.cols) * config.winW;
  const y = Math.floor(index / config.cols) * config.winH;

  const browser = await chromium.launch({
    headless: !config.headed,
    args: [
      `--window-position=${x},${y}`,
      `--window-size=${config.winW},${config.winH}`,
      // 창이 많으면 백그라운드 탭이 스로틀돼서 SSE 반응이 늦게 그려진다.
      "--disable-backgrounding-occluded-windows",
      "--disable-renderer-backgrounding",
      "--disable-background-timer-throttling",
    ],
  });

  const context = await browser.newContext({
    viewport: config.headed ? null : { width: config.winW, height: config.winH },
    locale: "ko-KR",
    timezoneId: "Asia/Seoul",
    // 드리미 매칭 화면이 브라우저 위치를 요구한다.
    permissions: ["geolocation"],
    geolocation: { latitude: user.lat, longitude: user.lng, accuracy: 20 },
    // PWA 서비스워커가 셸을 캐시해 오래된 화면을 보여주는 것을 막는다.
    serviceWorkers: "block",
    recordVideo: { dir: join(config.videoDir, user.label), size: { width: config.winW, height: config.winH } },
  });

  // 매칭 SSE를 테스트 쪽에서 직접 기록한다. 화면에 뜨는 문구로 판정하면 프론트가 안내를 어떻게
  // 그리느냐(혹은 그리지 않느냐)에 결과가 끌려간다 — 실제로 선착순에서 진 드리미는 안내가 카드에
  // 가려 보이지 않는다. 이벤트를 가로채기만 하고 그대로 흘려보내므로 앱 동작은 바뀌지 않는다.
  await context.addInitScript((types) => {
    window.__sse = [];
    const Native = window.EventSource;
    window.EventSource = class extends Native {
      constructor(...args) {
        super(...args);
        for (const type of types) {
          super.addEventListener(type, (e) => window.__sse.push({ type, data: e.data, at: Date.now() }));
        }
      }
    };
  }, SSE_TYPES);

  const page = await context.newPage();

  // Vite dev 서버는 첫 방문에서 의존성을 최적화한 뒤 페이지를 통째로 리로드한다.
  // 리로드가 입력값과 진행 중인 제출을 날려버리므로, 로드가 잦아든 뒤에 폼을 채우고
  // 그래도 /login에 남아 있으면 한 번 더 시도한다.
  await page.goto(`${config.webBase}/login`, { waitUntil: "networkidle" });
  for (let attempt = 1; ; attempt++) {
    await page.locator('input[type="email"]').fill(user.email);
    await page.locator('input[type="password"]').fill(user.password);
    await page.getByRole("button", { name: "로그인", exact: true }).click();
    try {
      await page.waitForURL("**/home", { timeout: 20_000 });
      break;
    } catch (e) {
      if (attempt >= 2) throw e;
      await page.waitForLoadState("networkidle");
    }
  }

  return { browser, context, page };
}

/**
 * 드리미: 역할 전환 → 매칭 화면 진입 → 하단 "시작하기"로 온라인 전환.
 *
 * 화면 진입만으로 도는 것은 주변 콜 조회(지도용 폴링)뿐이다. 콜을 받는 상태(`POST
 * /api/v1/dreami/status/online`)는 `MatchingScreen`의 "시작하기" 버튼이 호출하므로, 누르지 않으면
 * 오퍼가 오지 않아 매칭 대기에서 그대로 타임아웃난다.
 */
async function prepareDreami(user, page, config) {
  await page.getByRole("tab", { name: "드리미" }).click();
  // 역할 전환은 서버 검증(DREAMI.request_cd=APPROVED)을 거친다. 미승인이면 /verify로 튄다.
  await page.waitForTimeout(500);
  if (page.url().includes("/verify")) {
    throw new Error("드리미 미승인 계정 — 시드가 DREAMI.request_cd를 APPROVED로 넣었는지 확인하세요");
  }
  await page.getByRole("button", { name: "드리미 시작하기" }).click();
  await page.waitForURL("**/matching", { timeout: 20_000 });

  await page.getByRole("button", { name: "시작하기", exact: true }).click();

  // 전환 결과는 지도 아래 카드에 나온다. 성공이면 "근방 3km 내 부름 N건 대기중",
  // 실패면 "콜을 받을 수 없는 상태예요" + 사유(수행 중인 주문이 있는 계정 등).
  const online = page.getByText(/부름 \d+건 대기중/);
  const failed = page.getByText("콜을 받을 수 없는 상태예요");
  const outcome = await Promise.race([
    online.waitFor({ state: "visible", timeout: 20_000 }).then(() => "온라인", () => null),
    failed.waitFor({ state: "visible", timeout: 20_000 }).then(() => "실패", () => null),
  ]);
  if (outcome !== "온라인") {
    const reason =
      outcome === "실패"
        ? await failed.locator("xpath=following-sibling::p").innerText().catch(() => "")
        : "";
    throw new Error(`드리미 온라인 전환 실패${reason ? `: ${reason}` : " — 상태 카드가 뜨지 않았습니다."}`);
  }

  return { screen: "/matching" };
}

/**
 * 부르미: 부름 등록은 4단계 위저드(주소검색·물품·사진·결제)라 UI로 몰기엔 취약하다.
 * 로그인된 페이지 컨텍스트에서 세션 쿠키를 그대로 태워 API로 등록하고 화면만 매칭 대기로 보낸다 —
 * 드리미 수락 팝업은 실제 SSE로 받는다.
 */
async function prepareBoormi(user, page, config) {
  const orderId = await page.evaluate(async (order) => {
    const res = await fetch("/api/v1/boormi/calls", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      credentials: "include",
      body: JSON.stringify(order),
    });
    const body = await res.json();
    if (!res.ok) throw new Error(`부름 등록 실패 ${res.status}: ${JSON.stringify(body)}`);
    return body.result;
  }, user.order);

  await page.goto(`${config.webBase}/matching?orderId=${orderId}`);
  return { screen: "/matching", orderId };
}

/** 준비된 화면에서 매칭이 잡히는지 지켜보고 단언한다. */
async function assertMatched(user, page, config, shotDir) {
  const button = user.role === "dreami" ? "콜 수락" : "수락하기";
  const target = user.role === "dreami" ? "**/delivery-track**" : "**/delivery-detail**";

  const startedAt = Date.now();
  const locator = page.getByRole("button", { name: button });
  await locator.waitFor({ state: "visible", timeout: config.timeoutMs });

  const popupShot = join(shotDir, `${user.label}-popup.png`);
  await page.screenshot({ path: popupShot });

  // 수락 버튼을 누르기 전까지 쌓인 이벤트는 판정에서 제외한다. 드리미 등록 단계에서도
  // offer_error("이미 등록된 드리미입니다.")가 올 수 있어, 그걸 패배로 세면 안 된다.
  const mark = await page.evaluate(() => window.__sse?.length ?? 0);

  await locator.click();

  // 수락 즉시 화면이 넘어가지 않는다. 드리미 수락 → 부르미 확정 → 배달 시작(delivery_started_* SSE)
  // 순서로 진행되고, 화면 이동은 마지막 SSE를 받은 시점에 일어난다.
  //
  // 같은 오퍼를 여러 드리미가 동시에 받으므로 선착순에서 지는 것은 정상 동작이다.
  // 화면 전환과 패배 통지 중 먼저 오는 쪽으로 판정한다 — 지는 것을 실패로 세면 리포트가 늘 빨갛다.
  // 패배 통지는 offer_closed("선착순 마감") 또는 offer_error("이미 다른 드리미가 수락한 주문입니다.")로 온다.
  const stage = await Promise.race(
    [
      page.waitForURL(target, { timeout: 30_000 }).then(() => "완료", () => null),
      user.role === "dreami"
        ? page
            .waitForFunction(
              ([from, types]) => window.__sse.slice(from).some((e) => types.includes(e.type)),
              [mark, LOSS_TYPES],
              { timeout: 30_000 },
            )
            .then(() => "선착순패배", () => null)
        : null,
    ].filter(Boolean),
  );
  if (stage === null) {
    throw new Error(`수락 후 화면 전환도, 패배 통지도 오지 않았습니다. 수신 SSE: ${await sseDump(page, mark)}`);
  }

  const doneShot = join(shotDir, `${user.label}-done.png`);
  await page.screenshot({ path: doneShot });

  return { stage, elapsedMs: Date.now() - startedAt, screenshots: [popupShot, doneShot] };
}

/**
 * 브라우저 세션을 띄우고 매칭 대기까지 진입시킨다.
 * 반환된 핸들의 `finish()`가 각 창의 단언 결과를 모아 돌려주고 브라우저를 닫는다.
 */
export async function startWatch({ users, config, log }) {
  await mkdir(config.videoDir, { recursive: true });
  const shotDir = join(config.resultDir, "screenshots");
  await mkdir(shotDir, { recursive: true });

  const sessions = [];
  const results = [];

  // 순차 launch. 동시에 여러 개를 띄우면 크롬 프로세스 스파이크로 첫 로그인들이 타임아웃난다.
  for (const [index, user] of users.entries()) {
    const result = {
      label: user.label,
      email: user.email,
      role: user.role,
      stage: "시작",
      matched: false,
      elapsedMs: null,
      screenshots: [],
      error: null,
    };
    results.push(result);
    try {
      const session = await openUser(user, index, config);
      sessions.push(session);
      result.stage = "로그인";
      const prepared =
        user.role === "dreami"
          ? await prepareDreami(user, session.page, config)
          : await prepareBoormi(user, session.page, config);
      result.stage = "대기중";
      result.orderId = prepared.orderId ?? null;
      session.user = user;
      session.result = result;
      log(`${user.label} (${user.role}) 로그인 ✓ ${prepared.screen} ✓ 대기중`);
    } catch (e) {
      result.stage = "준비실패";
      result.error = e.message;
      log(`${user.label} 준비 실패: ${e.message}`);
    }
  }

  const ready = sessions.filter((s) => s.result?.stage === "대기중");

  // 준비된 창들은 지금부터 각자 팝업을 기다린다. 부하는 이 사이에 돈다.
  const watching = ready.map(async (session) => {
    try {
      const outcome = await assertMatched(session.user, session.page, config, shotDir);
      Object.assign(session.result, { matched: true, ...outcome });
    } catch (e) {
      session.result.stage = session.result.stage === "대기중" ? "매칭대기_타임아웃" : session.result.stage;
      session.result.error = e.message.split("\n")[0];
      // 화면만으로는 "안 왔다"와 "왔는데 안 그렸다"가 구분되지 않는다. 받은 SSE를 남긴다.
      session.result.sse = await sseDump(session.page);
      await session.page.screenshot({ path: join(shotDir, `${session.user.label}-fail.png`) }).catch(() => {});
      session.result.screenshots.push(join(shotDir, `${session.user.label}-fail.png`));
    }
  });

  return {
    readyCount: ready.length,
    total: users.length,
    /** 브라우저 부르미가 만든 주문. 부하 원장 밖의 주문이라 리포트가 잔여 상태와 구분하는 데 쓴다. */
    orderIds: results.map((r) => r.orderId).filter(Boolean),
    async finish() {
      await Promise.all(watching);
      await Promise.all(
        sessions.map(async ({ browser, context }) => {
          await context.close().catch(() => {});
          await browser.close().catch(() => {});
        }),
      );
      return results;
    },
  };
}

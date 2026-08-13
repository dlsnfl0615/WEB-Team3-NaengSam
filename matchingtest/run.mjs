/**
 * 매칭 부하테스트 진입점. 이 파일 하나만 실행하면 된다.
 *
 *   npm run loadtest                             # config/.env.local 설정으로 전체 실행
 *   ENV_FILE=config/.env.prod npm run loadtest   # 다른 환경 대상
 *   node run.mjs --only=reset             # 단계 하나만
 *   node run.mjs --only=cleanup           # 남은 활성 주문만 취소 (CLEANUP_CONFIRM=1 필요)
 *
 * 5단계를 순서대로 돌고 각 단계의 진행을 그대로 출력한다.
 *   1) 대상 확인  2) DB 초기화  3) 유저 세팅  4) 클라이언트 기동  5) 부하 + 검증
 *
 * 환경변수는 config/env.example 참고. 대상(API/WEB/DB)이 전부 변수라 로컬과 실서버를 같은 스크립트로 때린다.
 */
import { existsSync } from "node:fs";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { join } from "node:path";
import { monitorEventLoopDelay } from "node:perf_hooks";

// ── .env 로드 (다른 모듈이 import 시점에 env를 읽으므로 반드시 먼저) ──
const ENV_FILE = process.env.ENV_FILE ?? "./config/.env.local";
if (existsSync(ENV_FILE)) {
  for (const line of (await readFile(ENV_FILE, "utf8")).split("\n")) {
    const m = /^\s*([A-Z0-9_]+)\s*=\s*(.*)$/.exec(line);
    if (!m) continue;
    const quoted = /^\s*(["'])(.*)\1\s*$/.exec(m[2]);
    // 따옴표가 없으면 값 앞이나 공백 뒤에 오는 `#`부터를 주석으로 본다.
    const value = quoted ? quoted[2] : m[2].replace(/(^|\s)#.*$/, "").trim();
    // 셸에서 준 값이 파일보다 우선한다.
    if (process.env[m[1]] === undefined) process.env[m[1]] = value;
  }
}

const num = (name, fallback) => Number(process.env[name] ?? fallback);
const bool = (name, fallback) => (process.env[name] ?? fallback) === "1";

const config = {
  apiBase: process.env.API_BASE ?? "http://localhost:8080",
  webBase: process.env.WEB_BASE ?? "http://localhost:5173",
  dbKind: (process.env.DB_KIND ?? "h2").toLowerCase(),
  dbUrl: process.env.DB_URL ?? "jdbc:h2:tcp://localhost/~/test;MODE=MySQL",

  dreamiCount: num("DREAMI_COUNT", 100),
  boormiCount: num("BOORMI_COUNT", 100),
  orderCount: num("ORDER_COUNT", 100),
  orderRate: num("ORDER_RATE", 5),
  loginConcurrency: num("LOGIN_CONCURRENCY", 10),
  acceptDelayMs: num("ACCEPT_DELAY_MS", 0),
  durationMs: num("DURATION_MS", 120_000),
  drainMs: num("DRAIN_MS", 20_000),

  // 배달 단계. 픽업/완료 시각은 건별로 아래 범위 안에서 균등분포로 뽑는다.
  delivery: bool("DELIVERY", "1"),
  locationIntervalMs: num("LOCATION_INTERVAL_MS", 5_000),
  pickupMsMin: num("PICKUP_MS_MIN", 20_000),
  pickupMsMax: num("PICKUP_MS_MAX", 90_000),
  deliverMsMin: num("DELIVER_MS_MIN", 30_000),
  deliverMsMax: num("DELIVER_MS_MAX", 180_000),

  watch: bool("WATCH", "1"),
  watchDreami: num("WATCH_DREAMI", 10),
  watchBoormi: num("WATCH_BOORMI", 10),
  headed: bool("HEADED", "0"),
  watchTimeoutMs: num("WATCH_TIMEOUT_MS", 60_000),
  watchConcurrency: num("WATCH_CONCURRENCY", 4),
  // 브라우저 창이 배달을 UI로 완주시킬 때의 시간들. 부하의 PICKUP_MS_*/DELIVER_MS_*와 별개다 —
  // 이 창이 재는 것은 배달 소요 시간이 아니라 화면이 끝까지 동작하는지다.
  watchPickupMs: num("WATCH_PICKUP_MS", 2_000),
  watchDeliverMs: num("WATCH_DELIVER_MS", 2_000),
  watchDeliveryTimeoutMs: num("WATCH_DELIVERY_TIMEOUT_MS", 300_000),

  useExistingAccounts: bool("USE_EXISTING_ACCOUNTS", "0"),
  existingEmailLike: process.env.EXISTING_EMAIL_LIKE ?? "",
  existingPassword: process.env.EXISTING_PASSWORD ?? "",

  cleanup: bool("CLEANUP", "1"),
  cleanupConfirm: bool("CLEANUP_CONFIRM", "0"),

  resetDb: bool("RESET_DB", "1"),
  allowRemoteReset: bool("ALLOW_REMOTE_RESET", "0"),
  kakaoCheck: bool("KAKAO_CHECK", "1"),
  corsCheck: bool("CORS_CHECK", "1"),

  resultDir: process.env.RESULT_DIR ?? "./result",
  videoDir: process.env.VIDEO_DIR ?? "./videos",
  usersFile: process.env.BROWSER_USERS_FILE ?? "./config/users.json",
  agentsFile: process.env.AGENTS_OUT ?? "./agents.json",
  sqlFile: process.env.SQL_OUT ?? "./seed.sql",
  cols: num("COLS", 4),
  winW: num("WIN_W", 420),
  winH: num("WIN_H", 780),
};

const only = process.argv.find((a) => a.startsWith("--only="))?.slice("--only=".length) ?? null;
const runs = (stage) => only === null || only === stage;

// env 로드 후에 불러야 각 모듈이 올바른 설정을 읽는다.
const db = await import("./modules/db.mjs");
const { createLedger, reconcile, TIMEOUTS } = await import("./modules/ledger.mjs");
const { buildSeed, writeSeed } = await import("./modules/seed.mjs");
const { pullAgents } = await import("./modules/pull.mjs");
const { runDrive, requestStop, createClient } = await import("./modules/drive.mjs");
const { cleanupOrders } = await import("./modules/cleanup.mjs");
const { buildReport } = await import("./modules/report.mjs");

// ── 출력 ─────────────────────────────────────────────────────────────
const BAR = "─".repeat(46);
const clock = () => new Date().toTimeString().slice(0, 8);
let stageNo = 0;

function header(title) {
  stageNo++;
  console.log(`\n━━ ${stageNo}/5  ${title} ${BAR}`);
}
const item = (label, ...rest) => console.log(`  ${label.padEnd(6, " ")}`, ...rest);
const line = (text) => console.log(`  ${text}`);
const warn = (text) => console.log(`  ⚠  ${text}`);

// ── 1. 대상 확인 ─────────────────────────────────────────────────────
async function checkTargets() {
  header("대상 확인");

  const probe = async (name, url) => {
    const at = Date.now();
    try {
      await fetch(url, { signal: AbortSignal.timeout(5000) });
      item(name, url, `✓ 응답 ${Date.now() - at}ms`);
      return true;
    } catch (e) {
      item(name, url, `✗ ${e.message}`);
      return false;
    }
  };

  // 원격 초기화 가드는 DB에 붙기 전에 본다. 붙은 다음이면 연결 실패가 가드보다 먼저 터져
  // "왜 막혔는지"가 아니라 "왜 못 붙는지"만 보이게 된다.
  assertResetAllowed();

  const apiOk = await probe("API", config.apiBase);
  if (!apiOk) throw new Error(`API에 닿지 않습니다: ${config.apiBase} — 백엔드가 떠 있는지 확인하세요.`);
  if (config.watch) {
    await probe("WEB", config.webBase);
    if (config.corsCheck) await checkCors();
  }

  const tables = await db.ping();
  item("DB", `${config.dbKind}  ${config.dbUrl}`, `✓ ${tables}개 테이블`);

  warn("매칭 엔진은 인메모리다. 이전 런의 방이 남아 있으면 결과가 오염된다 — 백엔드 재시작 권장.");
}

/**
 * 백엔드가 WEB_BASE 오리진을 CORS로 허용하는지 미리 본다.
 *
 * Vite dev 서버는 `/api` 프록시로 요청을 넘기면서 브라우저의 Origin 헤더를 그대로 전달한다.
 * 백엔드 기본 허용 오리진은 `http://localhost:5173` 하나뿐이라(`application.properties`의
 * `cors.allowed-origins`), 프론트를 다른 포트로 띄우면 모든 API가 403 "Invalid CORS request"로 막힌다.
 * 브라우저 안에서만 터지므로 로그에는 "준비 실패"로만 보인다 — 여기서 먼저 잡는다.
 *
 * 프론트와 API가 같은 오리진인 배포 환경(CDN이 /api를 백엔드로 프록시)에서는 서버가 CORS 헤더를 줄
 * 이유가 없어 이 검사가 거짓 실패를 낸다. 그런 대상에는 CORS_CHECK=0을 준다.
 */
async function checkCors() {
  const origin = new URL(config.webBase).origin;
  const hint =
    `백엔드를 CORS_ALLOWED_ORIGINS=${origin} 로 다시 띄우거나(콤마로 여러 개 가능), WEB_BASE를 허용된 오리진으로 맞추세요.`;

  let res;
  try {
    res = await fetch(`${config.apiBase}/api/v1/user/me`, {
      method: "OPTIONS",
      headers: { Origin: origin, "Access-Control-Request-Method": "GET" },
      signal: AbortSignal.timeout(5000),
    });
  } catch (e) {
    throw new Error(`CORS 확인 실패: ${e.message}\n  ${hint}`);
  }

  if (!res.headers.get("access-control-allow-origin")) {
    throw new Error(`백엔드가 ${origin} 오리진을 거부합니다 (HTTP ${res.status}).\n  ${hint}`);
  }
  item("CORS", `${origin} → ${config.apiBase}`, "✓ 허용");
}

/** 원격 DB를 초기화하려는 실행을 막는다. 1단계와 2단계 양쪽에서 부른다. */
function assertResetAllowed() {
  if (!config.resetDb) return;
  if (db.isLocalDb() || config.allowRemoteReset) return;
  throw new Error(
    `원격 DB(${db.dbHost()})를 초기화하려 했습니다. 실계정·실결제에 영향을 줄 수 있어 중단합니다.\n` +
      "  정말 필요하면 ALLOW_REMOTE_RESET=1을, 아니면 RESET_DB=0을 주세요.",
  );
}

// ── 2. DB 초기화 ─────────────────────────────────────────────────────
async function resetDb() {
  header("DB 초기화");
  if (!config.resetDb) {
    line("RESET_DB=0 — 초기화를 건너뛰고 기존 계정을 재사용한다.");
    return;
  }
  assertResetAllowed();

  line(`대상  ${db.TEST_EMAIL_SUFFIX} 계정과 그 계정이 만든 주문만 (전체 삭제 아님)`);
  const before = await db.snapshot();
  const at = Date.now();
  const { deleted, remaining } = await db.resetTestData();

  const deletedText = Object.entries(deleted)
    .map(([t, n]) => `${t} ${n}`)
    .join("  ");
  line(`이전  ${Object.entries(before).map(([t, n]) => `${t} ${n}`).join("  ")}`);
  line(`삭제  ${deletedText || "없음"}`);
  const leftover = Object.entries(remaining).filter(([, n]) => n > 0);
  line(`잔여  ${Object.entries(remaining).map(([t, n]) => `${t} ${n}`).join("  ")}   ${leftover.length === 0 ? "✓ 초기화 완료" : "✗ 잔여 있음"} ${((Date.now() - at) / 1000).toFixed(1)}s`);
  if (leftover.length > 0) {
    throw new Error(`초기화 후에도 시드 데이터가 남아 있습니다: ${leftover.map(([t, n]) => `${t}=${n}`).join(", ")}`);
  }
}

// ── 3. 유저 세팅 ─────────────────────────────────────────────────────
async function seedUsers() {
  header("유저 세팅");
  const browserUsers = JSON.parse(await readFile(config.usersFile, "utf8"));
  const watchUsers = pickWatchUsers(browserUsers);
  const center = {
    lat: num("CENTER_LAT", 37.4979),
    lng: num("CENTER_LNG", 127.0276),
    spread: num("SPREAD_DEG", 0.01),
  };

  // 운영처럼 INSERT를 할 수 없는 대상. 계정을 만들지 않고 이미 있는 계정을 SELECT로 끌어다 쓴다.
  if (config.useExistingAccounts) {
    line(`수집  DB에 있는 계정 재사용 (INSERT 없음) — 패턴 ${config.existingEmailLike}`);
    await checkLeftoverOrders();
    const at = Date.now();
    const agents = await pullAgents({
      emailLike: config.existingEmailLike,
      password: config.existingPassword,
      dreamiCount: config.dreamiCount,
      boormiCount: config.boormiCount,
      center,
      excludeEmails: browserUsers.map((u) => u.email),
      hex: db.HEX(),
    });
    await writeFile(config.agentsFile, `${JSON.stringify(agents, null, 2)}\n`, "utf8");
    line(`수집  드리미 ${agents.dreamis.length} / 부르미 ${agents.boormis.length} → ${config.agentsFile}   ✓ ${((Date.now() - at) / 1000).toFixed(1)}s`);
    return { agents, watchUsers };
  }

  line(`생성  드리미 ${config.dreamiCount} / 부르미 ${config.boormiCount} / 브라우저 계정 ${browserUsers.length}`);
  const { sql, agents } = buildSeed({
    dreamiCount: config.dreamiCount,
    boormiCount: config.boormiCount,
    browserUsers,
    center,
  });
  await writeSeed({ sqlOut: config.sqlFile, agentsOut: config.agentsFile, sql, agents });

  const at = Date.now();
  const applied = await db.runScriptFile(config.sqlFile);
  line(`적용  ${config.sqlFile} ${sql.split("\n").length.toLocaleString()}줄 실행 → ${applied}행   ✓ ${((Date.now() - at) / 1000).toFixed(1)}s`);

  const after = await db.snapshot();
  line(`확인  BOORMI ${after.BOORMI}  DREAMI ${after.DREAMI}  ORDERS ${after.ORDERS}  ✓`);
  return { agents, watchUsers };
}

/**
 * 백엔드가 카카오 스텁(`kakao.enabled=false`)으로 떠 있는지 확인한다. 주문 1건이 지오코딩 2회 + 길찾기 1회를
 * 태우므로, 설정을 빠뜨린 채 돌리면 카카오 쿼터를 다 쓰고 나서야 알게 된다.
 *
 * 3/5 유저 세팅 뒤에 부른다 — `/api/v1/address/place`는 `@PublicApi`가 없어 로그인 세션이 필요하고,
 * 시드 계정은 이 시점에야 존재한다.
 */
async function checkKakaoStub(agents) {
  if (!config.kakaoCheck) return;
  const seed = agents.boormis[0];
  if (!seed) return;

  const { call, login } = createClient(config.apiBase);
  // 부하 드라이버가 나중에 같은 계정으로 다시 로그인하므로 원본 agent를 건드리지 않는다.
  const probe = { email: seed.email, password: seed.password };
  const body = {
    origin: seed.order.originAddressLine1,
    originDetail: seed.order.originAddressLine2,
    destination: seed.order.destinationAddressLine1,
    destinationDetail: seed.order.destinationAddressLine2,
  };

  const samples = [];
  let first = null;
  try {
    await login(probe);
    // 첫 호출은 DispatcherServlet 초기화와 JIT 워밍업이 섞여 스텁도 수십 ms가 나온다 — 재보다 버린다.
    for (let i = 0; i < 3; i++) {
      const at = Date.now();
      const res = await call(probe, "POST", "/api/v1/address/place", body);
      if (i > 0) samples.push(Date.now() - at);
      if (first === null) first = res;
      else if (JSON.stringify(res) !== JSON.stringify(first)) {
        throw new Error("같은 주소인데 좌표가 매번 다릅니다 — 견적과 주문의 좌표가 어긋납니다.");
      }
    }
  } catch (e) {
    throw new Error(`카카오 스텁 확인 실패: ${e.message}\n  KAKAO_CHECK=0으로 건너뛸 수 있습니다.`);
  }

  const fastest = Math.min(...samples);
  if (fastest >= KAKAO_STUB_MAX_MS) {
    throw new Error(
      `실 카카오 API로 보입니다(/address/place 최소 ${fastest}ms). 주문 ${config.orderCount}건 = 호출 ${config.orderCount * 3}회.\n` +
        "  백엔드를 KAKAO_ENABLED=false로 다시 띄우세요. 의도한 실행이면 KAKAO_CHECK=0을 주세요.",
    );
  }
  item("카카오", `스텁 (kakao.enabled=false)`, `✓ /address/place 최소 ${fastest}ms`);
}

/** 외부 왕복은 최소 수십 ms(실측 190ms 안팎)라 로컬 인메모리 계산(한 자릿수 ms)과 이 선에서 확실히 갈린다. */
const KAKAO_STUB_MAX_MS = 50;

/**
 * 요청한 수만큼 계정이 없으면 조용히 줄이지 않고 멈춘다.
 * 예전에는 slice로 잘라서, users.json에 부르미가 없으면 부르미 화면 경로가 통째로 빠진 채
 * "통과"가 나왔다.
 */
function pickWatchUsers(browserUsers) {
  const picked = [];
  for (const [role, want] of [
    ["dreami", config.watchDreami],
    ["boormi", config.watchBoormi],
  ]) {
    const have = browserUsers.filter((u) => u.role === role);
    if (have.length < want) {
      throw new Error(
        `실클라이언트 ${role} 요청 ${want}명 / ${config.usersFile}에 ${have.length}명.\n` +
          `  ${config.usersFile}을 채우거나 WATCH_${role.toUpperCase()} 값을 낮추세요.`,
      );
    }
    picked.push(...have.slice(0, want));
  }
  return picked;
}

// ── 4. 클라이언트 기동 ───────────────────────────────────────────────
async function startClients(watchUsers) {
  header("클라이언트 기동 (Playwright)");
  if (!config.watch || watchUsers.length === 0) {
    line("WATCH=0 — 실클라이언트 검증을 건너뛴다.");
    return null;
  }
  const { startWatch } = await import("./modules/watch.mjs");
  const watcher = await startWatch({
    users: watchUsers,
    config: {
      webBase: config.webBase,
      headed: config.headed,
      timeoutMs: config.watchTimeoutMs,
      concurrency: config.watchConcurrency,
      pickupHoldMs: config.watchPickupMs,
      deliverHoldMs: config.watchDeliverMs,
      deliveryTimeoutMs: config.watchDeliveryTimeoutMs,
      videoDir: config.videoDir,
      resultDir: config.resultDir,
      cols: config.cols,
      winW: config.winW,
      winH: config.winH,
    },
    log: line,
  });
  line(`✓ ${watcher.readyCount}/${watcher.total} 준비`);
  if (watcher.readyCount === 0) {
    warn("준비된 창이 없다 — 프론트(WEB_BASE)가 떠 있는지 확인하세요. 부하는 그대로 진행한다.");
  }
  return watcher;
}

// ── 5. 부하 ──────────────────────────────────────────────────────────
async function drive(agents, ledger) {
  header("부하 시작");
  line(`목표  주문 ${config.orderCount}건 (초당 ${config.orderRate}) · 완주 상한 ${Math.min(config.orderCount, config.dreamiCount)}건`);
  if (config.delivery) {
    const holdMin = (config.pickupMsMin + config.deliverMsMin) / 1000;
    const holdMax = (config.pickupMsMax + config.deliverMsMax) / 1000;
    // 동시 배달 ≈ 초당 주문 수 × 평균 배달 소요 시간. 규모를 잡을 때 이 값을 먼저 본다.
    const expected = Math.round((config.orderRate * (holdMin + holdMax)) / 2);
    line(`배달  1건당 ${holdMin}~${holdMax}초 유지 · 위치 전송 ${config.locationIntervalMs}ms 주기`);
    line(`      예상 동시 배달 ≈ ${expected}건 (드리미 계정 ${config.dreamiCount}명이 상한)`);
  }

  // 브라우저 부르미도 주문을 하나씩 만들고 브라우저 드리미도 같은 풀에서 매칭된다.
  // 수요(주문)와 공급(드리미)이 같으면 오퍼가 한 번만 만료돼도 남는 주문이 굶는다.
  if (config.watch) {
    const demand = config.orderCount + config.watchBoormi;
    const supply = config.dreamiCount + config.watchDreami;
    line(`      브라우저 포함 주문 ${demand} vs 드리미 ${supply}`);
    if (demand >= supply) {
      line(`      ⚠ 여유가 없다. 오퍼 만료 한 번에 주문이 굶는다 — ORDER_COUNT를 줄이거나 DREAMI_COUNT를 늘려라.`);
    }
  }

  let lastTick = 0;
  return runDrive({
    ledger,
    dreamis: agents.dreamis,
    boormis: agents.boormis,
    config: {
      apiBase: config.apiBase,
      loginConcurrency: config.loginConcurrency,
      acceptDelayMs: config.acceptDelayMs,
      orderCount: config.orderCount,
      orderRate: config.orderRate,
      durationMs: config.durationMs,
      drainMs: config.drainMs,
      delivery: config.delivery,
      locationIntervalMs: config.locationIntervalMs,
      pickupMsMin: config.pickupMsMin,
      pickupMsMax: config.pickupMsMax,
      deliverMsMin: config.deliverMsMin,
      deliverMsMax: config.deliverMsMax,
    },
    log: line,
    tick: (s) => {
      if (Date.now() - lastTick < 2000) return;
      lastTick = Date.now();
      const live = ledger.finalize({
        finishedAt: Date.now(),
        dreamiCount: s.online,
      });
      const p95 = live.latency.server.createReqToOffer.p95;
      // 배달을 켰으면 동시 배달 수가 이 테스트의 주 지표다. 매칭 지표 뒤에 붙여 같이 본다.
      const deliveryPart = config.delivery
        ? ` | 배달중 ${s.activeDeliveries}(피크 ${live.concurrency.peak}) | ` +
          `픽업 ${s.pickupOk} 완료 ${s.finishOk} 실패 ${s.deliveryFail}`
        : "";
      console.log(
        `  ${clock()}  주문 ${s.created}/${s.target} | 오퍼 ${s.offers} | ` +
          `수락제출 ${s.acceptSubmitted}(실패 ${s.acceptFail}) | 선착순승 ${live.won} | ` +
          `확정 ${s.confirmOk}(실패 ${s.confirmFail}) | ` +
          `완주 ${live.completed} | 유실 ${live.missing.length} | 주문요청→오퍼 p95 ${p95 === null ? "-" : `${p95}ms`}` +
          deliveryPart,
      );
    },
  });
}

// ── 검증 + 리포트 ────────────────────────────────────────────────────
/** 이벤트 루프 지연 히스토그램을 밀리초 통계로 바꾼다. SSE 수신 시각이 밀린 크기의 상한이다. */
function eventLoopStats(histogram) {
  const toMs = (ns) => Math.round(ns / 1e5) / 10;
  return {
    p50: toMs(histogram.percentile(50)),
    p95: toMs(histogram.percentile(95)),
    p99: toMs(histogram.percentile(99)),
    max: toMs(histogram.max),
  };
}

/**
 * 브라우저 창이 UI로 배달을 끝낸 주문. 원장은 이 전이를 보지 못하므로 DB 대조와 정리 단계가
 * 이 목록으로 보정한다. 드리미 창은 자기가 완주시킨 주문을, 부르미 창은 완료 화면까지 본 자기
 * 주문을 들고 있다 — 둘 다 "이미 종료된 주문"이라는 점에서 같다.
 */
function browserDeliveredOrderIds(watchResults) {
  return (watchResults ?? []).filter((r) => r.delivered && r.orderId).map((r) => r.orderId);
}

async function verifyAndReport({ summary, agents, watchResults, startedAt, eventLoopLag }) {
  console.log(`\n━━ 검증  DB 대조 ${BAR}`);
  const dbRows = await db.verifyOrders(summary.orderIds);
  const duplicates = await db.duplicateAssignments(summary.orderIds);
  line(`주문 ${summary.orderIds.length}건 조회 → DB에서 ${dbRows.length}건 확인`);

  const dreamiIdByEmail = new Map(agents.dreamis.map((d) => [d.email, d.boormiId]));
  const mismatches = reconcile({
    records: summary.records,
    dbRows,
    dreamiIdByEmail,
    duplicates,
    browserDeliveredOrderIds: browserDeliveredOrderIds(watchResults),
  });

  const finishedAt = Date.now();
  const report = buildReport({
    summary,
    mismatches,
    watchResults,
    config: { ...config, timeouts: TIMEOUTS },
    startedAt,
    finishedAt,
    eventLoopLag,
  });

  console.log(report.text);

  await mkdir(config.resultDir, { recursive: true });
  const tag = new Date(startedAt).toISOString().replace(/[:.]/g, "-").slice(0, 19);
  const jsonPath = join(config.resultDir, `${tag}.json`);
  const mdPath = join(config.resultDir, `${tag}.md`);
  await writeFile(jsonPath, `${JSON.stringify(report.json, null, 2)}\n`, "utf8");
  await writeFile(mdPath, report.markdown, "utf8");
  console.log(`  리포트  ${jsonPath}\n          ${mdPath}\n`);

  return report.passed;
}

// ── 정리 ─────────────────────────────────────────────────────────────
/** 활성 주문을 `상태 n건` 문자열로 묶는다. 배달 단계까지 붙여야 어느 API로 치울지가 보인다. */
function summarizeStates(rows) {
  const counts = new Map();
  for (const r of rows) {
    const key = `${r.orderCd}${r.deliveryCd ? `/${r.deliveryCd}` : ""}`;
    counts.set(key, (counts.get(key) ?? 0) + 1);
  }
  return [...counts].sort().map(([state, n]) => `${state} ${n}`);
}

/**
 * 계정 수집 직전에 잔여 활성 주문을 세어 보여준다.
 *
 * 활성 주문 1건이 부르미 1명과 드리미 1명을 수집 대상에서 뺀다. 이걸 안 보여주면 계정이 모자랄 때
 * "패턴에 맞는 계정이 없다"로만 보여서, 실제 원인(이전 런의 잔여 주문)에 닿는 데 한참 걸린다.
 *
 * 경고만 하고 멈추지 않는다 — 계정이 충분하면 잔여 주문이 있어도 실행에 지장이 없고,
 * 취소는 되돌릴 수 없어서 부하 실행의 부작용으로 일어나면 안 된다.
 */
async function checkLeftoverOrders() {
  const rows = await db.activeOrdersByEmail(config.existingEmailLike);
  if (rows.length === 0) {
    line("잔여  활성 주문 없음 ✓");
    return;
  }
  line(`잔여  활성 주문 ${rows.length}건 — ${summarizeStates(rows).join(" · ")}`);
  warn("이 주문들이 부르미·드리미 계정을 잠급니다. npm run cleanup 으로 확인하세요.");
}

/**
 * 이번 런이 만든 주문을 전부 취소한다. 반드시 검증·리포트가 끝난 뒤에 부른다 —
 * `duplicateAssignments()`가 `order_cd = 'IN_PROGRESS'`를 보기 때문에 먼저 취소하면 리포트가 거짓 실패를 낸다.
 */
async function cleanupStage({ summary, agents, watchResults, watchUsers }) {
  console.log(`\n━━ 정리  주문 취소 ${BAR}`);

  const passwordOf = new Map([
    ...agents.boormis.map((b) => [b.email, b.password]),
    ...watchUsers.map((u) => [u.email, u.password]),
  ]);

  // 배달이 끝난 주문은 COMPLETED라 계정을 잠그지 않는다 — 취소를 시도할 이유가 없다.
  const finished = summary.records.filter((r) => r.finishOk === true);
  // 픽업은 끝났는데 완료를 못 한 주문. cancel/boormi·dreami·admin 전부 DELIVERING에서는
  // CANCELLATION_RESTRICTED_DURING_DELIVERY를 던지므로 API로 되돌릴 방법이 없다.
  // 부르미와 드리미 계정이 그대로 잠긴 채 남으니 목록을 그대로 보여주고 사람 손에 맡긴다.
  const stuckInDelivery = summary.records.filter((r) => r.pickupOk === true && r.finishOk !== true);
  // 브라우저 창이 UI로 끝낸 주문도 COMPLETED다. 빼지 않으면 취소를 걸었다가 DELIVERY_013만 받는다.
  const browserDelivered = browserDeliveredOrderIds(watchResults);
  const untouchable = new Set([
    ...[...finished, ...stuckInDelivery].map((r) => r.orderId),
    ...browserDelivered,
  ]);

  if (finished.length > 0) line(`배달 완료 ${finished.length}건은 이미 종료 — 취소 대상 아님`);
  if (browserDelivered.length > 0) {
    line(`브라우저 창이 완주시킨 ${browserDelivered.length}건도 이미 종료 — 취소 대상 아님`);
  }
  if (stuckInDelivery.length > 0) {
    warn(
      `DELIVERING에 멈춘 주문 ${stuckInDelivery.length}건은 API로 취소할 수 없습니다 (수동 정리 필요).`,
    );
    for (const r of stuckInDelivery.slice(0, 20)) line(`  ${r.orderId}  ${r.boormiEmail}`);
    if (stuckInDelivery.length > 20) line(`  … 외 ${stuckInDelivery.length - 20}건`);
  }

  const targets = [
    // 확정까지 간 주문은 IN_PROGRESS라 배달 취소로 가야 한다. 원장이 이미 알고 있으니 힌트로 넘긴다.
    ...summary.records
      .filter((r) => r.boormiEmail && !untouchable.has(r.orderId))
      .map((r) => ({
        orderId: r.orderId,
        email: r.boormiEmail,
        orderCd: r.confirmOk ? "IN_PROGRESS" : undefined,
      })),
    // 브라우저 부르미가 만든 주문도 같은 방식으로 계정을 잠근다. 빼면 그 계정이 계속 묶인다.
    // 드리미 창의 orderId는 남의 주문이라 취소 권한이 없다 — 역할로 갈라낸다.
    ...(watchResults ?? [])
      .filter((r) => r.role === "boormi" && r.orderId && !untouchable.has(r.orderId))
      .map((r) => ({ orderId: r.orderId, email: r.email })),
  ].map((t) => ({ ...t, password: passwordOf.get(t.email) }));

  const unknown = targets.filter((t) => !t.password);
  if (unknown.length) warn(`비밀번호를 모르는 계정의 주문 ${unknown.length}건은 건너뜁니다.`);

  const known = targets.filter((t) => t.password);
  if (known.length === 0) {
    line("취소할 주문이 없습니다.");
    return;
  }

  line(`대상 ${known.length}건`);
  const result = await cleanupOrders({
    apiBase: config.apiBase,
    targets: known,
    concurrency: config.loginConcurrency,
    log: line,
  });
  for (const f of result.failed) line(`✗ ${f.orderId} (${f.email}) ${f.reason}`);
}

/**
 * `--only=cleanup`. 이전 런들이 남기고 간 잔여 주문을 DB에서 찾아 치운다.
 * 되돌릴 수 없으므로 `CLEANUP_CONFIRM=1` 없이는 목록만 보여주고 끝낸다.
 */
async function cleanupStandalone() {
  console.log(`\n━━ 정리  잔여 주문 ${BAR}`);

  if (!config.existingEmailLike) {
    throw new Error("EXISTING_EMAIL_LIKE가 없습니다 — 어떤 계정의 주문을 치울지 정해야 합니다.");
  }
  if (!config.existingPassword) {
    throw new Error("EXISTING_PASSWORD가 없습니다 — 부르미 본인 세션이 있어야 취소할 수 있습니다.");
  }

  const rows = await db.activeOrdersByEmail(config.existingEmailLike);
  line(`패턴 ${config.existingEmailLike} — 활성 주문 ${rows.length}건`);

  for (const state of summarizeStates(rows)) line(`  ${state}`);

  if (rows.length === 0) return true;

  // DELIVERING은 어떤 취소 API로도 되돌릴 수 없다. 시도해봐야 전부 실패로 쌓일 뿐이라 빼고 알린다.
  const stuck = rows.filter((r) => r.deliveryCd === "DELIVERING");
  const cancellable = rows.filter((r) => r.deliveryCd !== "DELIVERING");
  if (stuck.length > 0) {
    warn(`DELIVERING ${stuck.length}건은 API로 취소할 수 없습니다 (수동 정리 필요).`);
    for (const r of stuck.slice(0, 20)) line(`  ${r.orderId}  ${r.email}`);
    if (stuck.length > 20) line(`  … 외 ${stuck.length - 20}건`);
  }
  if (cancellable.length === 0) return stuck.length === 0;

  if (!config.cleanupConfirm) {
    warn("dry-run입니다. 실제로 취소하려면 CLEANUP_CONFIRM=1을 붙여 다시 실행하세요.");
    return true;
  }

  const result = await cleanupOrders({
    apiBase: config.apiBase,
    targets: cancellable.map((r) => ({ ...r, password: config.existingPassword })),
    concurrency: config.loginConcurrency,
    log: line,
  });
  for (const f of result.failed) line(`✗ ${f.orderId} (${f.email}) ${f.reason}`);
  return result.failed.length === 0;
}

// ── 실행 ─────────────────────────────────────────────────────────────
process.on("SIGINT", () => {
  console.log("\n  중단 요청 — 진행 중인 단계를 정리하고 리포트를 냅니다. 한 번 더 누르면 즉시 종료합니다.");
  requestStop();
  process.once("SIGINT", () => process.exit(130));
});

const startedAt = Date.now();

try {
  await checkTargets();

  if (runs("reset")) await resetDb();
  if (only === "reset") process.exit(0);

  // 잔여 주문 정리는 시드도 부하도 타지 않는다 — DB에서 대상을 직접 찾는다.
  if (only === "cleanup") process.exit((await cleanupStandalone()) ? 0 : 1);

  let agents;
  let watchUsers = [];
  if (runs("seed")) {
    ({ agents, watchUsers } = await seedUsers());
  } else {
    agents = JSON.parse(await readFile(config.agentsFile, "utf8"));
    watchUsers = pickWatchUsers(JSON.parse(await readFile(config.usersFile, "utf8")));
  }
  await checkKakaoStub(agents);
  if (only === "seed") process.exit(0);

  const watcher = runs("watch") ? await startClients(watchUsers) : null;

  let watchResults = null;
  if (only === "watch") {
    watchResults = (await watcher?.finish()) ?? null;
    console.log(`\n  브라우저 결과: ${JSON.stringify(watchResults, null, 2)}`);
    // 브라우저 부르미도 실주문을 만든다. 이 단계만 돌려도 계정이 잠기므로 똑같이 치운다.
    if (config.cleanup) {
      await cleanupStage({ summary: { records: [] }, agents, watchResults, watchUsers });
    }
    process.exit(watchResults?.every((r) => r.matched && r.delivered) ? 0 : 1);
  }

  const ledger = createLedger();
  // SSE 수신 시각은 전부 이 프로세스의 이벤트 루프를 거친다. 루프가 밀리면 서버 지표가 그만큼 부풀어
  // 보이므로, 부하가 도는 동안의 루프 지연을 함께 재서 지표의 신뢰 구간을 남긴다.
  const loopLag = monitorEventLoopDelay({ resolution: 20 });
  loopLag.enable();
  const driveResult = await drive(agents, ledger);
  loopLag.disable();

  watchResults = (await watcher?.finish()) ?? null;

  const summary = ledger.finalize({
    finishedAt: Date.now(),
    dreamiCount: driveResult.onlineDreamis,
    // 브라우저 부르미의 주문도 부하 드리미에게 오퍼가 가므로 원장에 이벤트만 남는다 — 잔여 상태와 구분한다.
    externalOrderIds: watcher?.orderIds ?? [],
  });

  const passed = await verifyAndReport({
    summary,
    agents,
    watchResults,
    startedAt,
    eventLoopLag: eventLoopStats(loopLag),
  });

  // 리포트가 나온 뒤에만 취소한다. 순서가 바뀌면 검증이 취소된 주문을 보고 전부 유실로 센다.
  if (config.cleanup) await cleanupStage({ summary, agents, watchResults, watchUsers });

  process.exit(passed ? 0 : 1);
} catch (e) {
  console.error(`\n✗ 중단: ${e.message}\n`);
  process.exit(2);
}

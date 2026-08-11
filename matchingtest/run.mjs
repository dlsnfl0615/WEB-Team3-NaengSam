/**
 * 매칭 부하테스트 진입점. 이 파일 하나만 실행하면 된다.
 *
 *   npm run loadtest                             # config/.env.local 설정으로 전체 실행
 *   ENV_FILE=config/.env.prod npm run loadtest   # 다른 환경 대상
 *   node run.mjs --only=reset             # 단계 하나만
 *
 * 5단계를 순서대로 돌고 각 단계의 진행을 그대로 출력한다.
 *   1) 대상 확인  2) DB 초기화  3) 유저 세팅  4) 클라이언트 기동  5) 부하 + 검증
 *
 * 환경변수는 config/env.example 참고. 대상(API/WEB/DB)이 전부 변수라 로컬과 실서버를 같은 스크립트로 때린다.
 */
import { existsSync } from "node:fs";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { join } from "node:path";

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

  watch: bool("WATCH", "1"),
  watchDreami: num("WATCH_DREAMI", 2),
  watchBoormi: num("WATCH_BOORMI", 2),
  headed: bool("HEADED", "0"),
  watchTimeoutMs: num("WATCH_TIMEOUT_MS", 60_000),

  resetDb: bool("RESET_DB", "1"),
  allowRemoteReset: bool("ALLOW_REMOTE_RESET", "0"),
  kakaoCheck: bool("KAKAO_CHECK", "1"),

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
const { runDrive, requestStop, createClient } = await import("./modules/drive.mjs");
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
    await checkCors();
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

  line(`생성  드리미 ${config.dreamiCount} / 부르미 ${config.boormiCount} / 브라우저 계정 ${browserUsers.length}`);
  const { sql, agents } = buildSeed({
    dreamiCount: config.dreamiCount,
    boormiCount: config.boormiCount,
    browserUsers,
    center: {
      lat: num("CENTER_LAT", 37.4979),
      lng: num("CENTER_LNG", 127.0276),
      spread: num("SPREAD_DEG", 0.01),
    },
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

function pickWatchUsers(browserUsers) {
  const dreamis = browserUsers.filter((u) => u.role === "dreami").slice(0, config.watchDreami);
  const boormis = browserUsers.filter((u) => u.role === "boormi").slice(0, config.watchBoormi);
  return [...dreamis, ...boormis];
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
    },
    log: line,
    tick: (s) => {
      if (Date.now() - lastTick < 2000) return;
      lastTick = Date.now();
      const live = ledger.finalize({ finishedAt: Date.now(), dreamiCount: s.online });
      const p95 = live.latency.offerToAccept.p95;
      console.log(
        `  ${clock()}  주문 ${s.created}/${s.target} | 오퍼 ${s.offers} | ` +
          `수락제출 ${s.acceptSubmitted}(실패 ${s.acceptFail}) | 선착순승 ${live.won} | ` +
          `확정 ${s.confirmOk}(실패 ${s.confirmFail}) | ` +
          `완주 ${live.completed} | 유실 ${live.missing.length} | 오퍼→수락 p95 ${p95 === null ? "-" : `${p95}ms`}`,
      );
    },
  });
}

// ── 검증 + 리포트 ────────────────────────────────────────────────────
async function verifyAndReport({ summary, agents, watchResults, startedAt }) {
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
  });

  const finishedAt = Date.now();
  const report = buildReport({
    summary,
    mismatches,
    watchResults,
    config: { ...config, timeouts: TIMEOUTS },
    startedAt,
    finishedAt,
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
    process.exit(watchResults?.every((r) => r.matched) ? 0 : 1);
  }

  const ledger = createLedger();
  const driveResult = await drive(agents, ledger);

  watchResults = (await watcher?.finish()) ?? null;

  const summary = ledger.finalize({
    finishedAt: Date.now(),
    dreamiCount: driveResult.onlineDreamis,
    // 브라우저 부르미의 주문도 부하 드리미에게 오퍼가 가므로 원장에 이벤트만 남는다 — 잔여 상태와 구분한다.
    externalOrderIds: watcher?.orderIds ?? [],
  });

  const passed = await verifyAndReport({ summary, agents, watchResults, startedAt });
  process.exit(passed ? 0 : 1);
} catch (e) {
  console.error(`\n✗ 중단: ${e.message}\n`);
  process.exit(2);
}

/**
 * DB 접근기.
 *
 * Node용 H2 드라이버가 없어서 h2 jar의 `org.h2.tools.Shell`을 자식 프로세스로 띄워 JDBC를 쓴다.
 * Shell은 `-driver`만 바꿔주면 아무 JDBC 드라이버나 물릴 수 있으므로 MySQL도 같은 코드로 붙는다.
 *
 * 조회 결과 파싱:
 *  - H2  : `CALL CSVWRITE(파일, '쿼리')`로 CSV를 뽑고 그 파일을 읽는다. 따옴표·NULL 처리를 DB가 한다.
 *  - MySQL: CSVWRITE가 없으므로 Shell의 list 모드(`컬럼: 값` 한 줄씩) 출력을 파싱한다.
 *
 * Shell은 SQL이 실패해도 종료 코드가 0이고 다음 문장을 계속 실행한다. 그래서 출력에 스택트레이스가
 * 섞였는지를 직접 검사해 실패로 올린다.
 */
import { spawn } from "node:child_process";
import { readFile, unlink, writeFile } from "node:fs/promises";
import { existsSync, readdirSync } from "node:fs";
import { homedir, tmpdir } from "node:os";
import { join } from "node:path";

const GRADLE_CACHE = join(homedir(), ".gradle/caches/modules-2/files-2.1");

export const dbConfig = {
  kind: (process.env.DB_KIND ?? "h2").toLowerCase(),
  url: process.env.DB_URL ?? "jdbc:h2:tcp://localhost/~/test;MODE=MySQL",
  user: process.env.DB_USER ?? "sa",
  password: process.env.DB_PASSWORD ?? "",
};

/** 시드 계정을 골라내는 식별자. 초기화 범위도 이 값으로 한정된다. */
export const TEST_EMAIL_SUFFIX = process.env.TEST_EMAIL_SUFFIX ?? "@test.local";

/** `jdbc:h2:tcp://localhost/~/test` / `jdbc:mysql://db:3306/x` 양쪽에서 호스트를 뽑는다. */
export function dbHost(url = dbConfig.url) {
  const m = /jdbc:[^:]+:(?:tcp:|ssl:)?\/\/([^/:;]+)/.exec(url);
  return m ? m[1] : "";
}

export function isLocalDb(url = dbConfig.url) {
  const host = dbHost(url);
  return host === "" || host === "localhost" || host === "127.0.0.1" || host === "::1";
}

// ── JDBC 드라이버 jar 찾기 ───────────────────────────────────────────

/** `~/.gradle/caches/.../<group>/<artifact>/<ver>/<sha>/<artifact>-<ver>.jar` 중 sources가 아닌 것. */
function findGradleJar(group, artifact) {
  const base = join(GRADLE_CACHE, group, artifact);
  if (!existsSync(base)) return null;
  for (const version of readdirSync(base)) {
    const versionDir = join(base, version);
    for (const sha of readdirSync(versionDir)) {
      const shaDir = join(versionDir, sha);
      const jar = readdirSync(shaDir).find((f) => f.endsWith(".jar") && !f.includes("-sources"));
      if (jar) return join(shaDir, jar);
    }
  }
  return null;
}

let cachedClasspath = null;

function classpath() {
  if (cachedClasspath) return cachedClasspath;
  if (process.env.JDBC_JARS) {
    cachedClasspath = process.env.JDBC_JARS;
    return cachedClasspath;
  }
  // Shell 자체가 h2 jar 안에 있으므로 MySQL을 쓸 때도 h2 jar는 항상 필요하다.
  const h2 = findGradleJar("com.h2database", "h2");
  if (!h2) {
    throw new Error(
      "h2 jar를 찾지 못했습니다. 백엔드를 한 번 빌드하거나 JDBC_JARS로 jar 경로를 직접 지정하세요.",
    );
  }
  const jars = [h2];
  if (dbConfig.kind === "mysql") {
    const mysql = findGradleJar("com.mysql", "mysql-connector-j");
    if (!mysql) {
      throw new Error(
        "mysql-connector-j jar를 찾지 못했습니다. JDBC_JARS로 jar 경로를 직접 지정하세요.",
      );
    }
    jars.push(mysql);
  }
  cachedClasspath = jars.join(":");
  return cachedClasspath;
}

// ── Shell 호출 ───────────────────────────────────────────────────────

function shellArgs(extra = []) {
  const args = [
    "-cp",
    classpath(),
    "org.h2.tools.Shell",
    "-url",
    dbConfig.url,
    "-user",
    dbConfig.user,
    "-password",
    dbConfig.password,
  ];
  if (dbConfig.kind === "mysql") args.push("-driver", "com.mysql.cj.jdbc.Driver");
  return [...args, ...extra];
}

/** Shell 출력에 SQL 예외가 섞였는지 본다. 종료 코드로는 알 수 없다. */
function findError(output) {
  if (!/\n\s+at (org\.h2|com\.mysql|java\.)/.test(output)) return null;
  const line = output
    .split("\n")
    .find((l) => /(Exception|Error)/.test(l) && !l.trimStart().startsWith("at "));
  return line?.trim() ?? "SQL 실행 중 예외 발생";
}

function runShell(stdin, extraArgs = []) {
  return new Promise((resolve, reject) => {
    const child = spawn("java", shellArgs(extraArgs), { stdio: ["pipe", "pipe", "pipe"] });
    let out = "";
    child.stdout.on("data", (c) => (out += c));
    child.stderr.on("data", (c) => (out += c));
    child.on("error", (e) =>
      reject(new Error(`java 실행 실패: ${e.message} (java가 PATH에 있어야 합니다)`)),
    );
    child.on("close", () => {
      const error = findError(out);
      if (error) reject(new Error(`${error}\n--- Shell 출력 ---\n${out.slice(-2000)}`));
      else resolve(out);
    });
    if (stdin !== null) child.stdin.end(stdin);
  });
}

// ── CSV ──────────────────────────────────────────────────────────────

/** RFC4180. CSVWRITE는 값을 항상 `"`로 감싸고 NULL만 따옴표 없는 빈 칸으로 쓴다. */
function parseCsv(text) {
  const rows = [];
  let row = [];
  let field = "";
  let quoted = false;
  let wasQuoted = false;
  let i = 0;

  const endField = () => {
    row.push(wasQuoted ? field : field === "" ? null : field);
    field = "";
    wasQuoted = false;
  };

  while (i < text.length) {
    const ch = text[i];
    if (quoted) {
      if (ch === '"') {
        if (text[i + 1] === '"') {
          field += '"';
          i += 2;
          continue;
        }
        quoted = false;
        i++;
        continue;
      }
      field += ch;
      i++;
      continue;
    }
    if (ch === '"') {
      quoted = true;
      wasQuoted = true;
      i++;
      continue;
    }
    if (ch === ",") {
      endField();
      i++;
      continue;
    }
    if (ch === "\r") {
      i++;
      continue;
    }
    if (ch === "\n") {
      endField();
      rows.push(row);
      row = [];
      i++;
      continue;
    }
    field += ch;
    i++;
  }
  if (field !== "" || wasQuoted || row.length > 0) {
    endField();
    rows.push(row);
  }

  if (rows.length === 0) return [];
  const header = rows[0].map((h) => h ?? "");
  return rows.slice(1).map((r) => Object.fromEntries(header.map((h, idx) => [h, r[idx] ?? null])));
}

// ── 공개 API ─────────────────────────────────────────────────────────

let csvSeq = 0;

/** SELECT 하나를 실행해 행 객체 배열을 돌려준다. 문장 끝의 `;`는 붙이지 않는다. */
export async function query(sql) {
  const clean = sql.trim().replace(/;$/, "");
  if (dbConfig.kind === "mysql") return queryViaListMode(clean);

  const file = join(tmpdir(), `naengsam-loadtest-${process.pid}-${csvSeq++}.csv`);
  try {
    await runShell(null, ["-sql", `CALL CSVWRITE('${file}', '${clean.replace(/'/g, "''")}')`]);
    return parseCsv(await readFile(file, "utf8"));
  } finally {
    await unlink(file).catch(() => {});
  }
}

/**
 * MySQL용. Shell의 list 모드는 행마다 `컬럼: 값`을 한 줄씩 찍고 빈 줄로 행을 구분한다.
 * 파이프 정렬 출력보다 값에 구분자가 섞일 위험이 적다.
 */
async function queryViaListMode(sql) {
  const out = await runShell(`list\n${sql};\n`);
  const rows = [];
  let current = null;
  for (const raw of out.split("\n")) {
    const line = raw.replace(/^sql> /, "").replace(/^\.\.\.> /, "");
    if (/^\(\d+ rows?, /.test(line.trim())) break;
    const m = /^([A-Za-z_][\w]*): (.*)$/.exec(line);
    if (m) {
      if (!current) {
        current = {};
        rows.push(current);
      }
      current[m[1]] = m[2] === "null" ? null : m[2];
    } else if (line.trim() === "") {
      current = null;
    }
  }
  return rows;
}

/**
 * 여러 문장을 한 JVM에서 순서대로 실행하고, DML 문장별 변경 행 수를 순서대로 돌려준다.
 * 문장은 `;`로 끝나야 한다.
 */
export async function exec(statements) {
  const list = Array.isArray(statements) ? statements : [statements];
  const out = await runShell(`${list.join("\n")}\n`);
  return [...out.matchAll(/\(Update count: (\d+),/g)].map((m) => Number(m[1]));
}


/** 파일 하나를 통째로 실행한다(시드 SQL 적용용). */
export async function runScriptFile(path) {
  const sql = await readFile(path, "utf8");
  const out = await runShell(`${sql}\n`);
  return [...out.matchAll(/\(Update count: (\d+),/g)].reduce((sum, m) => sum + Number(m[1]), 0);
}

/** 연결 확인. 스키마의 테이블 수를 돌려준다. */
export async function ping() {
  const rows = await query(
    "select count(*) as CNT from INFORMATION_SCHEMA.TABLES" +
      " where TABLE_SCHEMA not in ('INFORMATION_SCHEMA','information_schema','mysql','sys','performance_schema')",
  );
  return Number(rows[0]?.CNT ?? 0);
}

/** UUID 문자열을 SQL 이진 리터럴로. BINARY(16) 컬럼 비교용. */
export function bin(uuid) {
  return `X'${String(uuid).replace(/-/g, "").toUpperCase()}'`;
}

/** RAWTOHEX/HEX 결과를 비교 가능한 형태로 정규화한다. */
export function normHex(value) {
  return value == null ? null : String(value).replace(/-/g, "").toUpperCase();
}

const HEX = () => (dbConfig.kind === "mysql" ? "HEX" : "RAWTOHEX");

const TEST_BOORMIS = () =>
  `SELECT boormi_id FROM BOORMI WHERE email LIKE '%${TEST_EMAIL_SUFFIX}'`;
const TEST_ORDERS = () => `SELECT order_id FROM ORDERS WHERE boormi_id IN (${TEST_BOORMIS()})`;
const TEST_WALLETS = () => `SELECT wallet_id FROM WALLET WHERE boormi_id IN (${TEST_BOORMIS()})`;
const TEST_DELIVERIES = () =>
  `SELECT delivery_id FROM DELIVERY WHERE order_id IN (${TEST_ORDERS()})`;

/** 초기화 전후로 비교해 보여줄 테이블. */
export const SNAPSHOT_TABLES = ["BOORMI", "DREAMI", "ORDERS", "MATCHING", "DELIVERY"];

/** 시드 계정 범위의 행 수. 전체 건수가 아니라 테스트 데이터 건수다. */
export async function snapshot() {
  const rows = await query(
    `select
       (select count(*) from BOORMI where email like '%${TEST_EMAIL_SUFFIX}') as BOORMI,
       (select count(*) from DREAMI where dreami_id in (${TEST_BOORMIS()})) as DREAMI,
       (select count(*) from ORDERS where boormi_id in (${TEST_BOORMIS()})) as ORDERS,
       (select count(*) from MATCHING where order_id in (${TEST_ORDERS()})) as MATCHING,
       (select count(*) from DELIVERY where order_id in (${TEST_ORDERS()})) as DELIVERY`,
  );
  return Object.fromEntries(SNAPSHOT_TABLES.map((t) => [t, Number(rows[0]?.[t] ?? 0)]));
}

/**
 * 삭제 순서. 자식 테이블부터 부모로 올라간다.
 * `TRUNCATE`도, 조건 없는 `DELETE`도 쓰지 않는다 — 전부 시드 계정 범위로 한정된다.
 */
function deleteStatements() {
  return [
    ["DELIVERY_ACCIDENT", `DELETE FROM DELIVERY_ACCIDENT WHERE delivery_id IN (${TEST_DELIVERIES()})`],
    ["DELIVERY_CERTIFICATION", `DELETE FROM DELIVERY_CERTIFICATION WHERE delivery_id IN (${TEST_DELIVERIES()})`],
    ["RETURN_DELIVERY", `DELETE FROM RETURN_DELIVERY WHERE delivery_id IN (${TEST_DELIVERIES()})`],
    ["BOORMI_REVIEW", `DELETE FROM BOORMI_REVIEW WHERE order_id IN (${TEST_ORDERS()})`],
    ["DREAMI_REVIEW", `DELETE FROM DREAMI_REVIEW WHERE order_id IN (${TEST_ORDERS()})`],
    ["PARTNER_HANDOFF", `DELETE FROM PARTNER_HANDOFF WHERE order_id IN (${TEST_ORDERS()})`],
    ["PICKUP_CERTIFICATION", `DELETE FROM PICKUP_CERTIFICATION WHERE order_id IN (${TEST_ORDERS()})`],
    ["CANCEL", `DELETE FROM CANCEL WHERE order_id IN (${TEST_ORDERS()})`],
    ["ORDER_STATUS_HISTORY", `DELETE FROM ORDER_STATUS_HISTORY WHERE order_id IN (${TEST_ORDERS()})`],
    [
      "POINT_LEDGERS",
      `DELETE FROM POINT_LEDGERS WHERE wallet_id IN (${TEST_WALLETS()})`,
    ],
    [
      "MONEY_LEDGERS",
      `DELETE FROM MONEY_LEDGERS WHERE wallet_id IN (${TEST_WALLETS()})`,
    ],
    [
      "POINT_TX",
      `DELETE FROM POINT_TX WHERE wallet_id IN (${TEST_WALLETS()}) OR order_id IN (${TEST_ORDERS()})`,
    ],
    [
      "MONEY_TX",
      `DELETE FROM MONEY_TX WHERE wallet_id IN (${TEST_WALLETS()}) OR order_id IN (${TEST_ORDERS()})`,
    ],
    ["PAYMENT", `DELETE FROM PAYMENT WHERE boormi_id IN (${TEST_BOORMIS()})`],
    ["DELIVERY", `DELETE FROM DELIVERY WHERE order_id IN (${TEST_ORDERS()})`],
    ["MATCHING", `DELETE FROM MATCHING WHERE order_id IN (${TEST_ORDERS()})`],
    ["ORDERS", `DELETE FROM ORDERS WHERE boormi_id IN (${TEST_BOORMIS()})`],
    ["EXCHANGES", `DELETE FROM EXCHANGES WHERE wallet_id IN (${TEST_WALLETS()})`],
    ["POINT_WALLET", `DELETE FROM POINT_WALLET WHERE wallet_id IN (${TEST_WALLETS()})`],
    ["MONEY_WALLET", `DELETE FROM MONEY_WALLET WHERE wallet_id IN (${TEST_WALLETS()})`],
    ["WALLET", `DELETE FROM WALLET WHERE boormi_id IN (${TEST_BOORMIS()})`],
    ["ADDRESS", `DELETE FROM ADDRESS WHERE boormi_id IN (${TEST_BOORMIS()})`],
    ["PAYMENT_METHOD", `DELETE FROM PAYMENT_METHOD WHERE boormi_id IN (${TEST_BOORMIS()})`],
    ["UPLOAD_SESSION", `DELETE FROM UPLOAD_SESSION WHERE boormi_id IN (${TEST_BOORMIS()})`],
    ["BOORMI_REJECT_HISTORY", `DELETE FROM BOORMI_REJECT_HISTORY WHERE boormi_id IN (${TEST_BOORMIS()})`],
    ["SETTLEMENT_DETAILS", `DELETE FROM SETTLEMENT_DETAILS WHERE dreami_id IN (${TEST_BOORMIS()})`],
    [
      "DREAMI_REQUEST_DENIED_DETAILS",
      `DELETE FROM DREAMI_REQUEST_DENIED_DETAILS WHERE dreami_id IN (${TEST_BOORMIS()})`,
    ],
    ["DREAMI", `DELETE FROM DREAMI WHERE dreami_id IN (${TEST_BOORMIS()})`],
    ["BOORMI", `DELETE FROM BOORMI WHERE email LIKE '%${TEST_EMAIL_SUFFIX}'`],
  ];
}

/**
 * 시드 계정과 그 계정이 만든 주문만 지운다.
 *
 * 시드 계정이 아닌 부르미의 주문이 시드 드리미에게 배차돼 있으면 그 주문은 삭제 범위 밖인데
 * DREAMI 삭제가 FK에 걸린다. 남의 주문을 건드리는 대신 중단하고 사실을 보고한다.
 */
export async function resetTestData() {
  const foreign = await query(
    `select count(*) as CNT from ORDERS
      where dreami_id in (${TEST_BOORMIS()})
        and boormi_id not in (${TEST_BOORMIS()})`,
  );
  if (Number(foreign[0]?.CNT ?? 0) > 0) {
    throw new Error(
      `시드 계정이 아닌 부르미의 주문 ${foreign[0].CNT}건이 시드 드리미에게 배차돼 있습니다. ` +
        "남의 데이터를 건드리지 않기 위해 초기화를 중단합니다 — 해당 주문을 먼저 정리하세요.",
    );
  }

  const plan = deleteStatements();
  const counts = await exec(plan.map(([, sql]) => `${sql};`));
  const deleted = {};
  plan.forEach(([table], i) => {
    const n = counts[i] ?? 0;
    if (n > 0) deleted[table] = n;
  });
  return { deleted, remaining: await snapshot() };
}

/** 이번 런에서 만든 주문만 골라 매칭·배달 결과를 읽는다. */
export async function verifyOrders(orderIds) {
  if (orderIds.length === 0) return [];
  const list = orderIds.map(bin).join(", ");
  const hex = HEX();
  return query(
    `select ${hex}(o.order_id) as ORDER_ID,
            ${hex}(o.boormi_id) as BOORMI_ID,
            ${hex}(o.dreami_id) as ORDER_DREAMI_ID,
            o.order_cd as ORDER_CD,
            ${hex}(d.dreami_id) as DELIVERY_DREAMI_ID,
            d.delivery_cd as DELIVERY_CD,
            m.accepted_dtm as ACCEPTED_DTM
       from ORDERS o
       left join DELIVERY d on d.order_id = o.order_id
       left join MATCHING m on m.order_id = o.order_id
      where o.order_id in (${list})`,
  );
}

/** 주문 상태 전이 이력. SSE로 관측한 순서와 대조한다. */
export async function orderHistory(orderIds) {
  if (orderIds.length === 0) return [];
  const list = orderIds.map(bin).join(", ");
  return query(
    `select ${HEX()}(order_id) as ORDER_ID, previous_cd as PREVIOUS_CD, new_cd as NEW_CD, changed_dtm as CHANGED_DTM
       from ORDER_STATUS_HISTORY
      where order_id in (${list})
      order by order_id, changed_dtm`,
  );
}

/** 한 드리미가 동시에 여러 건을 들고 있는지 — 중복 배차의 DB 흔적. */
export async function duplicateAssignments(orderIds) {
  if (orderIds.length === 0) return [];
  const list = orderIds.map(bin).join(", ");
  return query(
    `select ${HEX()}(dreami_id) as DREAMI_ID, count(*) as CNT
       from ORDERS
      where order_id in (${list}) and dreami_id is not null and order_cd = 'IN_PROGRESS'
      group by dreami_id
     having count(*) > 1`,
  );
}

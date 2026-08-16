/**
 * 시드 계정 생성기.
 *
 * 계정만 만든다. 주문은 만들지 않는다 — 이번 테스트는 주문도 실경로(`POST /api/v1/boormi/calls`)로
 * 만들기 때문이다.
 *
 * 계정을 DB에 직접 넣는 이유: 회원가입 API는 SMS 인증을 요구하고, 포인트 충전 API도 드리미 승인 API도
 * 존재하지 않는다. 로그인 가능한 승인 드리미 100명을 API로 만들 방법이 없다.
 *
 * 비밀번호는 백엔드 `PasswordHasher`와 같은 규격(PBKDF2WithHmacSHA256 / salt 16B / 210,000회 /
 * 256bit, `"<saltHex>:<hashHex>"`)으로 해싱해 넣는다. 평문으로 넣으면 `UserService.login`의
 * `PasswordHasher.matches`가 항상 false라 로그인이 실패한다.
 *
 * 단독 실행:
 *   node modules/seed.mjs      # seed.sql + agents.json 생성 (DB 적용 없음, matchingtest/에서 실행)
 * 보통은 run.mjs가 이 모듈을 불러 SQL을 만들고 db.mjs로 바로 적용한다.
 */
import { pbkdf2Sync, randomBytes, randomUUID } from "node:crypto";
import { readFile, writeFile } from "node:fs/promises";

/** 시드 계정을 골라내는 식별자. 초기화 DELETE의 범위도 이 값이다. */
export const EMAIL_DOMAIN = "@test.local";
/** 시드로 만드는 계정의 공통 비밀번호. agents.json으로 그대로 나간다. */
export const SEED_PASSWORD = "test1234!";

/**
 * BINARY(16) 컬럼용. Hibernate가 UUID를 big-endian 16바이트로 저장하는 것과 같은 표현.
 * `UNHEX()`는 H2에 없으므로(MySQL 호환 모드 전용) 표준 이진 리터럴 `X'...'`을 쓴다 — 양쪽 다 받는다.
 */
function bin(uuid) {
  return `X'${uuid.replace(/-/g, "")}'`;
}

function quote(value) {
  return `'${String(value).replace(/'/g, "''")}'`;
}

/**
 * 백엔드 `PasswordHasher`와 동일한 규격으로 해싱한다.
 * PBKDF2WithHmacSHA256 / salt 16바이트 / 210,000회 / 256bit → `"<saltHex>:<hashHex>"`.
 */
const PBKDF2_ITERATIONS = 210_000;
const PBKDF2_KEY_BYTES = 32;
const PBKDF2_SALT_BYTES = 16;

/** 210,000회 반복이라 계정 수만큼 돌리면 느리다. 같은 평문은 한 번만 계산해 재사용한다(테스트 데이터). */
const hashCache = new Map();

function hashPassword(rawPassword) {
  let hashed = hashCache.get(rawPassword);
  if (!hashed) {
    const salt = randomBytes(PBKDF2_SALT_BYTES);
    const key = pbkdf2Sync(rawPassword, salt, PBKDF2_ITERATIONS, PBKDF2_KEY_BYTES, "sha256");
    hashed = `${salt.toString("hex")}:${key.toString("hex")}`;
    hashCache.set(rawPassword, hashed);
  }
  return hashed;
}

/**
 * 주문 생성이 카카오 지오코딩을 타므로 좌표가 아니라 실제 도로명주소여야 한다.
 * 검색이 실패하면 주문 생성이 통째로 실패하므로, 확실히 잡히는 강남·서초 일대 주소만 쓴다.
 */
const ROAD_ADDRESSES = [
  "서울 강남구 테헤란로 152",
  "서울 강남구 테헤란로 212",
  "서울 강남구 봉은사로 524",
  "서울 강남구 학동로 426",
  "서울 강남구 논현로 508",
  "서울 강남구 언주로 508",
  "서울 강남구 도산대로 130",
  "서울 강남구 삼성로 512",
  "서울 서초구 서초대로 398",
  "서울 서초구 강남대로 373",
  "서울 서초구 반포대로 58",
  "서울 서초구 사평대로 108",
];

const ITEM_NAMES = ["서류 봉투", "소형 박스", "USB 메모리", "계약서", "샘플 상자"];

/** 부르미 i번이 쓸 주문 payload. 출발지와 도착지가 같아지지 않게 어긋나게 집는다. */
export function orderPayload(i) {
  const origin = ROAD_ADDRESSES[i % ROAD_ADDRESSES.length];
  const destination = ROAD_ADDRESSES[(i + 1 + (i % 3)) % ROAD_ADDRESSES.length];
  return {
    originAddressLine1: origin,
    originAddressLine2: `${(i % 20) + 1}층`,
    destinationAddressLine1: destination === origin ? ROAD_ADDRESSES[(i + 5) % ROAD_ADDRESSES.length] : destination,
    destinationAddressLine2: "1층 로비",
    itemName: ITEM_NAMES[i % ITEM_NAMES.length],
    itemCd: "DOCUMENT",
    // OrderRequest.itemSizeCd 는 @NotNull 이라 빠지면 주문이 전부 400(COMMON_001)으로 막힌다.
    // 요금 배율이 1.0 인 S 로 고정한다 — 배율을 섞으면 요금 비교가 런마다 흔들린다.
    itemSizeCd: "S",
    itemDetail: "부하테스트용 주문",
    deliveryRequest: "부하테스트",
  };
}

/**
 * 계정 SQL과 에이전트 명세를 만든다. 파일에 쓰지는 않는다.
 * `browserUsers`는 Playwright가 쓸 계정(users.json). 시드 계정과 같은 DB 상태를 공유한다.
 */
export function buildSeed({ dreamiCount, boormiCount, browserUsers, pointAmount = 10_000_000, center }) {
  const lines = [
    "-- seed.mjs 생성물. 로컬/테스트 DB 전용 — 운영 DB에 절대 실행하지 마세요.",
    `-- 브라우저 계정 ${browserUsers.length}개 / 드리미 ${dreamiCount}명 / 부르미 ${boormiCount}명`,
    "",
  ];

  /** 전화번호가 unique 제약이라 계정 전체에 걸쳐 겹치지 않게 순번으로 발급한다. */
  let phoneSeq = 0;
  const nextPhone = () => `010${String(90_000_000 + phoneSeq++).padStart(8, "0")}`;

  const jitter = (base) => Number((base + (Math.random() - 0.5) * 2 * center.spread).toFixed(6));

  /**
   * BOORMI + 지갑 3종을 넣는다. `withDreami`면 승인된 DREAMI 행까지 넣는다.
   * created_dtm·*_avg_score는 MySQL DDL엔 DEFAULT가 있지만 Hibernate가 만든 스키마(H2 등)엔
   * NOT NULL만 있고 DEFAULT가 없다. 양쪽에서 다 돌게 전부 명시한다.
   */
  function pushAccount({ boormiId, email, password, name, withDreami }) {
    const id = bin(boormiId);
    const wallet = bin(randomUUID());

    lines.push(
      `-- ${name} ${email}`,
      "INSERT INTO BOORMI (boormi_id, email, password, name, phone_number, birthdate, user_cd, is_dreami_activated, boormi_avg_score, created_dtm)",
      `VALUES (${id}, ${quote(email)}, ${quote(hashPassword(password))}, ${quote(name)}, ${quote(nextPhone())}, DATE '1995-01-01', 'ACTIVE', ${withDreami ? "TRUE" : "FALSE"}, 0, CURRENT_TIMESTAMP);`,
      `INSERT INTO WALLET (wallet_id, boormi_id) VALUES (${wallet}, ${id});`,
      `INSERT INTO POINT_WALLET (wallet_id, amount, updated_dtm) VALUES (${wallet}, ${pointAmount}, CURRENT_TIMESTAMP);`,
      `INSERT INTO MONEY_WALLET (wallet_id, pending_amount, amount, updated_dtm) VALUES (${wallet}, 0, 0, CURRENT_TIMESTAMP);`,
    );

    if (withDreami) {
      // DREAMI는 BOORMI와 PK를 공유한다(dreami_id = boormi_id). 심사 통과 상태로 바로 넣는다.
      lines.push(
        "INSERT INTO DREAMI (dreami_id, request_cd, id_card_key, criminal_record_key, request_dtm, review_dtm, dreami_avg_score)",
        `VALUES (${id}, 'APPROVED', 'seed/id-card', 'seed/criminal-record', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);`,
      );
    }
    lines.push("");
  }

  // ── 1. 브라우저 창용 계정 (users.json) ──
  lines.push("-- 브라우저 창용 계정", "");
  for (const user of browserUsers) {
    pushAccount({
      boormiId: randomUUID(),
      email: user.email,
      password: user.password,
      name: user.label,
      withDreami: user.role === "dreami",
    });
  }

  // ── 2. 드리미 에이전트 ──
  lines.push("-- 드리미 에이전트 (drive.mjs가 로그인해 콜을 수락한다)", "");
  const dreamis = Array.from({ length: dreamiCount }, (_, i) => {
    const boormiId = randomUUID();
    const email = `dreami-agent-${i + 1}${EMAIL_DOMAIN}`;
    pushAccount({
      boormiId,
      email,
      password: SEED_PASSWORD,
      name: `드리미에이전트-${i + 1}`,
      withDreami: true,
    });
    return {
      boormiId,
      email,
      password: SEED_PASSWORD,
      lat: jitter(center.lat),
      lng: jitter(center.lng),
    };
  });

  // ── 3. 부르미 에이전트 ──
  // DREAMI 행을 만들지 않는다: 주문을 가진 계정은 `DreamiService.goOnline`의 `countActiveOrders > 0`
  // 가드에 걸려 드리미로 온라인 전환할 수 없다. 부르미와 드리미는 반드시 다른 계정이어야 한다.
  lines.push("-- 부르미 에이전트 (drive.mjs가 로그인해 주문을 만들고 드리미를 확정한다)", "");
  const boormis = Array.from({ length: boormiCount }, (_, i) => {
    const boormiId = randomUUID();
    const email = `boormi-agent-${i + 1}${EMAIL_DOMAIN}`;
    pushAccount({
      boormiId,
      email,
      password: SEED_PASSWORD,
      name: `부르미에이전트-${i + 1}`,
      withDreami: false,
    });
    return { boormiId, email, password: SEED_PASSWORD, order: orderPayload(i) };
  });

  return { sql: `${lines.join("\n")}\n`, agents: { dreamis, boormis } };
}

/** SQL과 에이전트 명세를 파일로 남긴다. seed.sql은 감사용이고, 적용은 run.mjs가 db.mjs로 한다. */
export async function writeSeed({ sqlOut, agentsOut, sql, agents }) {
  await writeFile(sqlOut, sql, "utf8");
  await writeFile(agentsOut, `${JSON.stringify(agents, null, 2)}\n`, "utf8");
}

// ── 단독 실행 ────────────────────────────────────────────────────────
if (import.meta.url === `file://${process.argv[1]}`) {
  const browserUsers = JSON.parse(await readFile(process.env.BROWSER_USERS_FILE ?? "./config/users.json", "utf8"));
  const { sql, agents } = buildSeed({
    dreamiCount: Number(process.env.DREAMI_COUNT ?? 100),
    boormiCount: Number(process.env.BOORMI_COUNT ?? 100),
    browserUsers,
    center: {
      lat: Number(process.env.CENTER_LAT ?? 37.4979),
      lng: Number(process.env.CENTER_LNG ?? 127.0276),
      spread: Number(process.env.SPREAD_DEG ?? 0.01),
    },
  });
  const sqlOut = process.env.SQL_OUT ?? "./seed.sql";
  const agentsOut = process.env.AGENTS_OUT ?? "./agents.json";
  await writeSeed({ sqlOut, agentsOut, sql, agents });
  console.error(
    `${sqlOut}: 계정 ${browserUsers.length + agents.dreamis.length + agents.boormis.length}개\n` +
      `${agentsOut}: 드리미 ${agents.dreamis.length}명 + 부르미 ${agents.boormis.length}명\n` +
      "DB 적용은 run.mjs(npm run loadtest)가 합니다.",
  );
}

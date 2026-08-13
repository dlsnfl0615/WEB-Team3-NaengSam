/**
 * 기존 계정 수집기.
 *
 * `seed.mjs`가 계정을 만들어 넣는 대신, 이미 DB에 있는 계정을 조회해 같은 모양의 에이전트 명세를 만든다.
 * 운영 DB처럼 INSERT를 할 수 없는 대상에 부하를 걸 때 쓴다. SELECT만 한다.
 *
 * 비밀번호는 DB에 해시로만 있어 복원할 수 없다. 대상 계정들이 공통으로 쓰는 평문을 `password`로 받아
 * 그대로 에이전트에 넣는다 — 틀리면 로그인 단계에서 전부 실패한다.
 *
 * 계정을 드리미용과 부르미용으로 갈라 뽑는 이유는 `seed.mjs`와 같다: 주문을 가진 계정은
 * `DreamiService.goOnline`의 `countActiveOrders > 0` 가드에 걸려 온라인 전환이 안 된다.
 */
import { CLOSED_ORDER_CDS, query } from "./db.mjs";
import { orderPayload } from "./seed.mjs";

function quote(value) {
  return `'${String(value).replace(/'/g, "''")}'`;
}

/** 활성 주문을 하나라도 들고 있는 계정을 걸러내는 조건. 부르미·드리미 양쪽 컬럼을 다 본다. */
const NO_ACTIVE_ORDER = `NOT EXISTS (
       SELECT 1 FROM ORDERS o
        WHERE (o.boormi_id = b.boormi_id OR o.dreami_id = b.boormi_id)
          AND o.order_cd NOT IN (${CLOSED_ORDER_CDS}))`;

/**
 * 승인된 드리미 계정. 활성 주문이 있으면 온라인 전환이 막히므로 제외한다.
 * DREAMI는 BOORMI와 PK를 공유한다(dreami_id = boormi_id).
 */
async function selectDreamiCandidates(hex, emailLike) {
  return query(
    `SELECT ${hex}(b.boormi_id) AS ID, b.email AS EMAIL
       FROM BOORMI b
       JOIN DREAMI d ON d.dreami_id = b.boormi_id
      WHERE b.email LIKE ${quote(emailLike)}
        AND d.request_cd = 'APPROVED'
        AND ${NO_ACTIVE_ORDER}
      ORDER BY b.email`,
  );
}

/**
 * 드리미가 아닌 계정. 드리미 풀과 겹치지 않게 DREAMI 행이 없는 계정만 쓴다.
 * 활성 주문이 이미 있으면 `BoormiService`의 동시 진행 5건 상한에 금방 걸리므로 함께 제외한다.
 */
async function selectBoormiCandidates(hex, emailLike) {
  return query(
    `SELECT ${hex}(b.boormi_id) AS ID, b.email AS EMAIL
       FROM BOORMI b
       LEFT JOIN DREAMI d ON d.dreami_id = b.boormi_id
      WHERE b.email LIKE ${quote(emailLike)}
        AND d.dreami_id IS NULL
        AND ${NO_ACTIVE_ORDER}
      ORDER BY b.email`,
  );
}

/**
 * 요청한 수만큼 없으면 조용히 줄이지 않고 멈춘다.
 * 계정이 모자란 채로 진행하면 "부하를 덜 걸었다"가 아니라 "통과했다"로 보이기 때문이다.
 */
function take(rows, want, role, emailLike) {
  if (rows.length < want) {
    throw new Error(
      `${role} 계정 ${want}개가 필요한데 DB에서 ${rows.length}개만 찾았습니다 (패턴 ${emailLike}).\n` +
        `  진행 중인 주문이 계정을 잠그고 있을 수 있습니다 — npm run cleanup 으로 확인하세요.\n` +
        `  그래도 모자라면 ${role === "드리미" ? "DREAMI_COUNT" : "BOORMI_COUNT"}를 낮추거나 EXISTING_EMAIL_LIKE를 넓히세요.`,
    );
  }
  return rows.slice(0, want);
}

/**
 * DB에 이미 있는 계정으로 `buildSeed()`와 같은 모양의 에이전트 명세를 만든다.
 *
 * `excludeEmails`는 Playwright가 쓸 계정(users.json). 같은 계정으로 브라우저와 드라이버가 동시에
 * 로그인하면 세션이 서로를 밀어내므로 드라이버 풀에서 빼둔다.
 */
export async function pullAgents({
  emailLike,
  password,
  dreamiCount,
  boormiCount,
  center,
  excludeEmails = [],
  hex,
}) {
  if (!emailLike) throw new Error("EXISTING_EMAIL_LIKE가 비어 있습니다 (예: %@test.local).");
  if (!password) throw new Error("EXISTING_PASSWORD가 비어 있습니다 — 대상 계정의 공통 평문 비밀번호가 필요합니다.");

  const excluded = new Set(excludeEmails);
  const usable = (rows) => rows.filter((r) => r.EMAIL && !excluded.has(r.EMAIL));

  const dreamiRows = take(
    usable(await selectDreamiCandidates(hex, emailLike)),
    dreamiCount,
    "드리미",
    emailLike,
  );
  const boormiRows = take(
    usable(await selectBoormiCandidates(hex, emailLike)),
    boormiCount,
    "부르미",
    emailLike,
  );

  const jitter = (base) => Number((base + (Math.random() - 0.5) * 2 * center.spread).toFixed(6));

  const dreamis = dreamiRows.map((row) => ({
    boormiId: row.ID,
    email: row.EMAIL,
    password,
    lat: jitter(center.lat),
    lng: jitter(center.lng),
  }));

  const boormis = boormiRows.map((row, i) => ({
    boormiId: row.ID,
    email: row.EMAIL,
    password,
    order: orderPayload(i),
  }));

  return { dreamis, boormis };
}

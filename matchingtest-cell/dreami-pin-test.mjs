/**
 * 부르미 "드리미를 찾는 중" 지도 핀 수동 테스트.
 *
 * 디버그 API로 픽업지 주변 임의 좌표에 드리미를 하나씩 등록하고, 잠시 유지한 뒤
 * 하나씩 제거한다. 기본은 Ctrl+C까지 반복하며 종료할 때 남은 드리미를 모두 정리한다.
 */

const apiBase = (process.env.API_BASE ?? "http://localhost:8080").replace(/\/$/, "");
const center = {
  lat: Number(process.env.CENTER_LAT ?? 37.4979),
  lng: Number(process.env.CENTER_LNG ?? 127.0276),
};
const count = Number(process.env.DREAMI_COUNT ?? 6);
const radiusMeters = Number(process.env.RADIUS_METERS ?? 700);
const stepMs = Number(process.env.STEP_MS ?? 1500);
const holdMs = Number(process.env.HOLD_MS ?? 8000);
const gapMs = Number(process.env.GAP_MS ?? 5000);
const cycles = Number(process.env.CYCLES ?? 0);

if (
  !Number.isFinite(center.lat) ||
  !Number.isFinite(center.lng) ||
  !Number.isInteger(count) ||
  count < 1 ||
  count > 10 ||
  !Number.isFinite(radiusMeters) ||
  radiusMeters <= 0
) {
  throw new Error("CENTER_LAT/LNG, RADIUS_METERS와 1~10 사이 DREAMI_COUNT를 확인하세요.");
}

const registered = new Set();
let stopping = false;
let cleaning = false;

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

function randomLocation() {
  const distance = Math.sqrt(Math.random()) * radiusMeters;
  const angle = Math.random() * Math.PI * 2;
  const northMeters = Math.cos(angle) * distance;
  const eastMeters = Math.sin(angle) * distance;
  const latitude = center.lat + northMeters / 111_320;
  const longitude =
    center.lng + eastMeters / (111_320 * Math.cos((center.lat * Math.PI) / 180));
  return {
    latitude: Number(latitude.toFixed(6)),
    longitude: Number(longitude.toFixed(6)),
  };
}

async function request(path, options) {
  const response = await fetch(`${apiBase}${path}`, options);
  const text = await response.text();
  if (!response.ok) {
    throw new Error(`${options.method} ${path} → ${response.status} ${text}`);
  }
  if (!text) return null;
  const body = JSON.parse(text);
  return body && typeof body === "object" && "result" in body ? body.result : body;
}

async function registerDreami() {
  const location = randomLocation();
  const dreamiId = await request("/api/v1/debug/matching/dreamis", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(location),
  });
  if (typeof dreamiId !== "string") throw new Error("드리미 ID를 받지 못했습니다.");
  registered.add(dreamiId);
  console.log(`+ ${dreamiId.slice(0, 8)}  ${location.latitude}, ${location.longitude}`);
}

async function removeDreami(dreamiId) {
  await request(`/api/v1/debug/matching/dreamis/${dreamiId}`, { method: "DELETE" });
  registered.delete(dreamiId);
  console.log(`- ${dreamiId.slice(0, 8)}`);
}

async function cleanup() {
  if (cleaning) return;
  cleaning = true;
  const ids = [...registered];
  if (ids.length > 0) console.log(`\n남은 드리미 ${ids.length}명 정리 중…`);
  await Promise.allSettled(ids.map(removeDreami));
}

async function run() {
  console.log(`API: ${apiBase}`);
  console.log(`중심: ${center.lat}, ${center.lng} / 반경 ${radiusMeters}m`);
  console.log("부르미 브라우저에서 같은 위치에 부름을 등록한 뒤 지도를 확인하세요. 종료: Ctrl+C\n");

  let completedCycles = 0;
  while (!stopping && (cycles === 0 || completedCycles < cycles)) {
    console.log(`[${completedCycles + 1}회차] 드리미 등록`);
    for (let i = 0; i < count && !stopping; i++) {
      await registerDreami();
      await sleep(stepMs);
    }
    if (stopping) break;

    console.log(`${holdMs}ms 유지`);
    await sleep(holdMs);

    console.log("드리미 제거");
    for (const dreamiId of [...registered]) {
      if (stopping) break;
      await removeDreami(dreamiId);
      await sleep(stepMs);
    }
    completedCycles++;
    if (!stopping && (cycles === 0 || completedCycles < cycles)) await sleep(gapMs);
  }
}

for (const signal of ["SIGINT", "SIGTERM"]) {
  process.once(signal, () => {
    stopping = true;
    void cleanup().finally(() => process.exit(0));
  });
}

try {
  await run();
} finally {
  await cleanup();
}

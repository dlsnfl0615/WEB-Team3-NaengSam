/**
 * 리포트 렌더러. 원장 집계·DB 대조·브라우저 결과를 사람이 읽는 형태로 만든다.
 * 콘솔용 텍스트와 저장용 마크다운은 같은 섹션 구성을 쓴다.
 */

const ms = (v) => (v === null || v === undefined ? "-" : `${v}ms`);

const API_LABEL = {
  create: "POST /boormi/calls",
  accept: "POST /dreami/offers/{id}/accept",
  confirm: "POST /boormi/calls/{id}/confirm",
  location: "POST /delivery/.../dreami-location",
  presign: "GET  /upload/url",
  upload: "PUT  presigned url",
  pickupFinish: "POST /delivery/.../pickup-finish",
  finish: "POST /delivery/.../finish",
};

const SERVER_LABEL = {
  createReqToOffer: "주문요청 → offer_popup",
  acceptReqToInfo: "수락요청 → dreami_info",
  confirmReqToDeliveryBoormi: "확정요청 → delivery_boormi",
  confirmReqToDeliveryDreami: "확정요청 → delivery_dreami",
  pickupReqToDelivering: "픽업요청 → delivery_delivering",
  finishReqToCompleted: "완료요청 → delivery_completed",
};

/**
 * 부르미 창이 완료 화면을 못 본 이유를 원장에서 되짚는다.
 *
 * 이 창은 `delivery_completed`를 기다릴 뿐 스스로 배달을 끝내지 못한다. 상대 드리미가 부하
 * 에이전트인데 제한 시간에 잘렸다면 프론트는 아무 잘못이 없다 — 그 구분을 여기서 짓는다.
 */
function boormiCompletionCause(result, recordById, browserDelivered) {
  const notReceived = "상대 드리미는 완주했다 — 부르미 화면이 delivery_completed를 받지 못했다";
  if (browserDelivered.has(result.orderId)) return notReceived;

  const rec = recordById.get(result.orderId);
  if (!rec) return "원장에 없는 주문 — 상대 드리미를 특정할 수 없다";
  if (rec.finishOk === true) return notReceived;
  if (rec.pickupOk === true) {
    return "상대 드리미(부하)가 픽업 후 완료까지 가지 못했다 — 프론트 문제가 아니다";
  }
  return "상대 드리미(부하)가 제한 시간에 잘렸다 — 프론트 문제가 아니다";
}

/** 동시 배달 수 추이를 한 줄로 압축한다. 폭이 넘치면 구간 최대값으로 다운샘플링한다. */
const SPARK = "▁▂▃▄▅▆▇█";

function sparkline(series, width = 60) {
  if (series.length === 0) return "";
  const step = Math.ceil(series.length / width);
  const buckets = [];
  for (let i = 0; i < series.length; i += step) {
    let peak = 0;
    for (let j = i; j < Math.min(series.length, i + step); j++) peak = Math.max(peak, series[j]);
    buckets.push(peak);
  }
  const max = Math.max(1, ...buckets);
  return { line: buckets.map((v) => SPARK[Math.floor((v / max) * 7.999)]).join(""), step };
}

function latencyRows(latency, label) {
  return Object.entries(latency).map(([key, s]) => ({
    stage: label[key] ?? key,
    count: s.count,
    p50: ms(s.p50),
    p95: ms(s.p95),
    p99: ms(s.p99),
  }));
}

function dreamiDistribution(byDreami, dreamiCount) {
  const entries = Object.entries(byDreami);
  const offers = entries.map(([, v]) => v.offers);
  const sorted = [...entries].sort((a, b) => b[1].offers - a[1].offers);
  return {
    받은계정: entries.length,
    못받은계정: dreamiCount - entries.length,
    최다: sorted[0] ? `${sorted[0][0]} (오퍼 ${sorted[0][1].offers})` : "-",
    최소: sorted.at(-1) ? `${sorted.at(-1)[0]} (오퍼 ${sorted.at(-1)[1].offers})` : "-",
    총오퍼: offers.reduce((a, b) => a + b, 0),
    수락제출: entries.reduce((n, [, v]) => n + v.submitted, 0),
    선착순승: entries.reduce((n, [, v]) => n + v.won, 0),
    완주: entries.reduce((n, [, v]) => n + v.completed, 0),
  };
}

export function buildReport({
  summary,
  mismatches,
  watchResults,
  config,
  startedAt,
  finishedAt,
  eventLoopLag = null,
}) {
  const c = summary.counters;
  const dist = dreamiDistribution(summary.byDreami, summary.dreamiCount);
  const unmatchedBoormis = Object.entries(summary.byBoormi).filter(([, v]) => v.unmatched.length > 0);

  // 배달을 켰다면 "구동을 시작한 배달은 전부 완료돼야 한다"가 통과 조건이다.
  // 확정 건수와 비교하지 않는 이유: 브라우저 드리미가 가져간 주문은 이 하네스가 몰지 않는다.
  const deliveryPassed = !config.delivery || summary.delivered === c.deliveryStarted;
  const passed = summary.missing.length === 0 && mismatches.length === 0 && deliveryPassed;
  // 매칭만 잡고 배달을 못 끝낸 창도 실패다 — 프론트의 배달 경로가 이 창들에서만 검증된다.
  const watchFailed = (watchResults ?? []).filter((r) => !r.matched || !r.delivered);
  /** 브라우저 드리미가 UI로 끝낸 주문. 하네스가 몬 배달과 분모가 달라 따로 센다. */
  const browserDelivered = new Set(
    (watchResults ?? [])
      .filter((r) => r.role === "dreami" && r.delivered && r.orderId)
      .map((r) => r.orderId),
  );
  const recordById = new Map((summary.records ?? []).map((r) => [r.orderId, r]));
  const conc = summary.concurrency ?? { peak: 0, series: [], bands: [] };

  const json = {
    startedAt: new Date(startedAt).toISOString(),
    finishedAt: new Date(finishedAt).toISOString(),
    config,
    throughput: {
      주문생성시도: c.createAttempt,
      주문생성성공: c.createOk,
      주문생성실패_카카오: c.createFailKakao,
      주문생성실패_동시제한: c.createFailLimit,
      주문생성실패_기타: c.createFailOther,
      오퍼발송: summary.offersSent,
      수락제출성공: c.acceptSubmitted,
      수락제출실패: c.acceptFail,
      선착순승: summary.won,
      확정성공: c.confirmOk,
      확정실패: c.confirmFail,
      완주: summary.completed,
      부르미거절: c.boormiRejected,
      오퍼오류: c.offerError,
      오퍼오류로_마감통지: summary.closedViaError.length,
      원장밖_이벤트: c.orphanEvents,
      브라우저가_만든_주문: summary.browserOrders.length,
      브라우저_드리미가_가져간_주문: summary.externalWinnerOrders.length,
      이_런이_만들지_않은_주문: summary.orphanOrders.length,
    },
    delivery: {
      구동시작: c.deliveryStarted,
      배달완료: summary.delivered,
      위치전송성공: c.locationOk,
      위치전송실패: c.locationFail,
      presign성공: c.presignOk,
      presign실패: c.presignFail,
      사진업로드성공: c.uploadOk,
      사진업로드실패: c.uploadFail,
      픽업완료성공: c.pickupOk,
      픽업완료실패: c.pickupFail,
      배달완료성공: c.finishOk,
      배달완료실패: c.finishFail,
      부르미_위치SSE: c.locationSse,
      부르미_배달중SSE: c.deliveringSse,
      부르미_완료SSE: c.completedSse,
      드리미_오프라인통지: c.dreamiOffline,
    },
    concurrency: conc,
    latency: summary.latency,
    eventLoopLag,
    dreamiDistribution: dist,
    byDreami: summary.byDreami,
    byBoormi: summary.byBoormi,
    missing: summary.missing,
    pending: summary.pending,
    capacityBlocked: summary.capacityBlocked.length,
    closedReasons: summary.closedReasons,
    errors: summary.errors,
    dbMismatches: mismatches,
    watch: watchResults ?? null,
    passed,
  };

  const L = [];
  const push = (...rest) => L.push(...rest);
  const pushLatency = (latency, label) => {
    for (const row of latencyRows(latency, label)) {
      push(
        `  ${row.stage.padEnd(34, " ")} n=${String(row.count).padStart(4)}` +
          `  p50 ${row.p50.padStart(8)}  p95 ${row.p95.padStart(8)}  p99 ${row.p99.padStart(8)}`,
      );
    }
  };

  push("", "═══ 매칭 부하테스트 리포트 ═══", "");
  push(
    `대상  API ${config.apiBase} / DB ${config.dbUrl}`,
    `규모  드리미 ${config.dreamiCount} · 부르미 ${config.boormiCount} · 주문 ${config.orderCount} (초당 ${config.orderRate})`,
    `기간  ${new Date(startedAt).toLocaleTimeString("ko-KR")} → ${new Date(finishedAt).toLocaleTimeString("ko-KR")}`,
    "",
  );

  push("── 1. 처리량 ──");
  push(
    `  주문 생성   시도 ${c.createAttempt} / 성공 ${c.createOk}` +
      (c.createFailKakao || c.createFailLimit || c.createFailOther
        ? `  (실패 — 카카오 ${c.createFailKakao} · 동시제한 ${c.createFailLimit} · 기타 ${c.createFailOther})`
        : ""),
    `  오퍼 발송   ${summary.offersSent}`,
    `  수락 제출   성공 ${c.acceptSubmitted} / 실패 ${c.acceptFail}  (200은 엔진 큐 제출일 뿐 — 선착순 승패가 아니다)`,
    `  선착순 승   ${summary.won} (dreami_info로 확정된 승자)`,
    `  부르미 확정 성공 ${c.confirmOk} / 실패 ${c.confirmFail}`,
    `  완주        ${summary.completed} (배달시작 SSE 수신 기준 — 확정 ${c.confirmOk}건 중` +
      `${summary.completed < c.confirmOk ? ` ${c.confirmOk - summary.completed}건은 배달이 시작되지 않았다` : " 전부 배달 시작"}` +
      `, 상한 ${Math.min(config.orderCount, summary.dreamiCount)} — 드리미 계정 수)`,
  );
  if (c.boormiRejected || c.offerError) push(`  기타        부르미거절 ${c.boormiRejected} · 오퍼오류 ${c.offerError}`);
  if (summary.closedViaError.length > 0) {
    push(`  패배 통지   offer_closed 대신 offer_error로 마감을 받은 오퍼 ${summary.closedViaError.length}건 (유실 아님)`);
  }
  if (summary.browserOrders.length > 0) {
    push(`  브라우저 주문 ${summary.browserOrders.length}건의 오퍼가 부하 드리미에게도 갔다 (정상)`);
  }
  if (summary.externalWinnerOrders.length > 0) {
    push(`  브라우저 드리미가 가져간 부하 주문 ${summary.externalWinnerOrders.length}건 (정상 — 배달 시작 통지는 브라우저로 갔다)`);
  }
  if (summary.orphanOrders.length > 0) {
    push(`  ⚠ 이 런이 만들지 않은 주문의 이벤트 ${summary.orphanOrders.length}건 — 이전 런의 인메모리 잔여 상태를 의심하세요.`);
  }
  if (c.orphanEvents) push(`  ⚠ 원장에 붙지 못한 이벤트 ${c.orphanEvents}건`);
  push("");

  push("── 2. 배달 ──");
  if (!config.delivery) {
    push("  실행하지 않음 (DELIVERY=0 — 매칭 확정까지만 측정)");
  } else {
    push(
      `  구동 시작   ${c.deliveryStarted} (delivery_started_dreami를 부하 드리미가 받은 주문)`,
      `  배달 완료   ${summary.delivered}/${c.deliveryStarted}` +
        (deliveryPassed ? " ✓" : ` ✗ — ${c.deliveryStarted - summary.delivered}건이 끝나지 못했다`),
      `  위치 전송   성공 ${c.locationOk} / 실패 ${c.locationFail}  (${config.locationIntervalMs}ms 주기)`,
      `  인증 사진   presign 성공 ${c.presignOk}/실패 ${c.presignFail} · PUT 성공 ${c.uploadOk}/실패 ${c.uploadFail}`,
      `  픽업 완료   성공 ${c.pickupOk} / 실패 ${c.pickupFail}`,
      `  배달 완료   성공 ${c.finishOk} / 실패 ${c.finishFail}`,
      `  부르미 SSE  위치 ${c.locationSse} · 배달중 ${c.deliveringSse} · 완료 ${c.completedSse}`,
    );
    if (browserDelivered.size > 0) {
      push(
        `  브라우저 완주 ${browserDelivered.size}건 (실클라이언트 드리미 창이 화면으로 끝낸 주문 —` +
          " 위 수치는 하네스가 몬 배달만 센다)",
      );
    }
    if (c.dreamiOffline > 0) {
      push(
        `  ⚠ 드리미 오프라인 통지 ${c.dreamiOffline}건 — 30초 넘게 위치가 끊긴 배달이 있었다.` +
          " 서버가 밀렸거나 하네스가 밀렸다는 신호다.",
      );
    }
    const deliveryErrors = Object.entries(summary.errors.delivery ?? {});
    if (deliveryErrors.length > 0) {
      push("  실패 사유:");
      for (const [message, n] of deliveryErrors.slice(0, 10)) push(`    ${n}건  ${message}`);
    }
  }
  push("");

  push("── 3. 동시 배달 ──");
  if (!config.delivery || conc.series.length === 0) {
    push("  측정 없음");
  } else {
    const spark = sparkline(conc.series);
    push(
      `  피크 ${conc.peak}건 (배달 시작 후 ${Math.round(conc.peakAtMs / 1000)}초 시점)`,
      `  추이 ${spark.line}`,
      `       ← ${conc.series.length}초 구간, 한 칸 ${spark.step}초 →`,
      "",
      "  요청 시점의 동시 배달 수로 나눈 지연 — 몇 건부터 무너지는지는 이 표에서 읽는다.",
      `  ${"동시 배달".padEnd(11)}${"요청".padStart(7)}${"실패".padStart(7)}` +
        `${"위치 p95".padStart(11)}${"위치 p99".padStart(11)}${"픽업 p95".padStart(11)}${"완료 p95".padStart(11)}`,
    );
    for (const b of conc.bands) {
      push(
        `  ${b.band.padEnd(11)}${String(b.total).padStart(7)}${String(b.fail).padStart(7)}` +
          `${ms(b.location.p95).padStart(11)}${ms(b.location.p99).padStart(11)}` +
          `${ms(b.pickupFinish.p95).padStart(11)}${ms(b.finish.p95).padStart(11)}`,
      );
    }
    push(
      "  ※ 구간의 표본 수는 그 구간에 머문 시간에 비례한다. 표본이 적은 구간의 p99는 신뢰하지 말 것.",
    );
  }
  push("");

  push("── 4. API 응답시간 (동기 서버 처리) ──");
  pushLatency(summary.latency.api, API_LABEL);
  push("  ※ 요청 발신부터 응답 수신까지. 톰캣 큐·트랜잭션·DB가 여기 들어간다. 매칭 비동기 경로는 빠져 있다.", "");

  push("── 5. 서버 비동기 경로 (요청 발신 → SSE 도착) ──");
  pushLatency(summary.latency.server, SERVER_LABEL);
  push("  ※ 기준점이 응답이 아니라 요청 발신이라 API 왕복까지 포함한다. 엔진이 응답보다 먼저 SSE를 보내도 음수가 되지 않는다.");
  if (eventLoopLag) {
    push(
      `  ${"harness 이벤트 루프 지연".padEnd(34, " ")}` +
        `        p50 ${ms(eventLoopLag.p50).padStart(8)}  p95 ${ms(eventLoopLag.p95).padStart(8)}  p99 ${ms(eventLoopLag.p99).padStart(8)}`,
    );
    push("  ※ SSE 수신 시각은 전부 이 프로세스의 이벤트 루프를 거친다. 이 값이 크면 위 지표가 그만큼 부풀어 보이니 서버를 탓하기 전에 먼저 본다.");
  }
  push("");

  push("── 6. 드리미별 분포 ──");
  push(
    `  오퍼를 받은 계정 ${dist.받은계정}/${summary.dreamiCount} · 한 번도 못 받은 계정 ${dist.못받은계정}`,
    `  총 오퍼 ${dist.총오퍼} · 수락 제출 ${dist.수락제출} · 선착순 승 ${dist.선착순승} · 완주 ${dist.완주}`,
    `  최다 ${dist.최다} · 최소 ${dist.최소}`,
    "  ※ 오퍼 배정은 2초 디바운스 배치 + 점수 기반 그리디(거리 − 주문대기 − 드리미대기)라 분포는 균등하지 않다 — 현 구현의 성질이다.",
    "",
  );

  push("── 7. 부르미별 분포 ──");
  push(`  주문을 만든 계정 ${Object.keys(summary.byBoormi).length} · 매칭 미성사 주문을 가진 계정 ${unmatchedBoormis.length}`);
  if (summary.capacityBlocked.length > 0) {
    push(`  완주 상한에 막혀 오퍼조차 못 받은 주문 ${summary.capacityBlocked.length}건 (유실 아님)`);
  }
  push("");

  push("── 8. 유실 알람 ──");
  if (summary.missing.length === 0) {
    push("  유실 없음 ✓");
  } else {
    push(`  ${summary.missing.length}건 유실 ✗`);
    const byStage = new Map();
    for (const m of summary.missing) byStage.set(m.stage, (byStage.get(m.stage) ?? 0) + 1);
    for (const [stage, n] of byStage) push(`    ${stage}: ${n}건`);
    for (const m of summary.missing.slice(0, 20)) {
      push(`    ${m.orderId}  ${m.stage}  대상 ${m.target ?? "-"}  대기 ${finishedAt - m.since}ms`);
    }
    if (summary.missing.length > 20) push(`    … 외 ${summary.missing.length - 20}건 (JSON 참고)`);
  }
  if (summary.pending.length > 0) {
    push(`  판정보류 ${summary.pending.length}건 — 제한 시간 전에 테스트가 끝났다. DRAIN_MS를 늘리세요.`);
  }
  push("");

  push("── 9. DB 대조 ──");
  if (mismatches.length === 0) {
    push("  불일치 없음 ✓ (ORDER_CD·MATCHING·DELIVERY·배차 드리미 전부 관측과 일치)");
  } else {
    push(`  ${mismatches.length}건 불일치 ✗`);
    for (const m of mismatches.slice(0, 30)) {
      push(`    ${m.orderId}  ${m.kind}${m.detail ? `  ${m.detail}` : ""}`);
    }
    if (mismatches.length > 30) push(`    … 외 ${mismatches.length - 30}건 (JSON 참고)`);
  }
  push("");

  push("── 10. 실클라이언트(Playwright) ──");
  if (!watchResults) {
    push("  실행하지 않음 (WATCH=0)");
  } else {
    // 역할별로 몇 명을 요청했고 실제로 몇 명이 돌았는지 먼저 적는다.
    // 이 줄이 없어서 부르미 0명으로 돈 실행이 "드리미만 나온 리포트"로 보였다.
    const ran = (role) => watchResults.filter((r) => r.role === role).length;
    push(`  실행 드리미 ${ran("dreami")}/${config.watchDreami} · 부르미 ${ran("boormi")}/${config.watchBoormi}`);
    for (const r of watchResults) {
      const mark = r.matched && r.delivered ? "✓" : "✗";
      push(
        `  ${mark} ${r.label} (${r.role}) 매칭 ${r.matched ? "✓" : "✗"} · 배달 ${r.delivered ? "✓" : "✗"}` +
          `  ${r.stage}` +
          (r.deliveryStage ? ` → ${r.deliveryStage}` : "") +
          (r.elapsedMs !== null && r.elapsedMs !== undefined ? ` ${r.elapsedMs}ms` : "") +
          (r.attempts > 1 ? ` 수락 ${r.attempts}회` : "") +
          (r.error ? `  — ${r.error}` : ""),
      );
      if (r.deliveryError) push(`      배달 실패 사유 ${r.deliveryError}`);
      // 부르미 창의 완료 대기는 상대 드리미가 끝내야 풀린다. 원장을 되짚지 않으면
      // 상대가 못 끝낸 것을 프론트 결함으로 읽게 된다.
      if (r.matched && !r.delivered && r.role === "boormi") {
        push(`      ${boormiCompletionCause(r, recordById, browserDelivered)}`);
      }
      if (r.sse) push(`      수신 SSE ${r.sse}`);
      for (const shot of r.screenshots ?? []) push(`      ${shot}`);
    }
  }
  push("");

  push(passed && watchFailed.length === 0 ? "결과: 통과 ✓" : "결과: 실패 ✗");
  push("");

  const markdown = [
    `# 매칭 부하테스트 리포트 (${new Date(startedAt).toISOString()})`,
    "",
    "```",
    ...L,
    "```",
    "",
  ].join("\n");

  return { text: L.join("\n"), markdown, json, passed: passed && watchFailed.length === 0 };
}

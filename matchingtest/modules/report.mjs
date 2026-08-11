/**
 * 리포트 렌더러. 원장 집계·DB 대조·브라우저 결과를 사람이 읽는 형태로 만든다.
 * 콘솔용 텍스트와 저장용 마크다운은 같은 섹션 구성을 쓴다.
 */

const ms = (v) => (v === null || v === undefined ? "-" : `${v}ms`);

function latencyRows(latency) {
  const label = {
    createToOffer: "주문생성 → 오퍼발송",
    offerToAccept: "오퍼발송 → 수락제출",
    acceptToInfo: "수락제출 → dreami_info",
    infoToConfirm: "dreami_info → 부르미확정",
    confirmToDelivery: "부르미확정 → 배달시작",
  };
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

export function buildReport({ summary, mismatches, watchResults, config, startedAt, finishedAt }) {
  const c = summary.counters;
  const dist = dreamiDistribution(summary.byDreami, summary.dreamiCount);
  const unmatchedBoormis = Object.entries(summary.byBoormi).filter(([, v]) => v.unmatched.length > 0);

  const passed = summary.missing.length === 0 && mismatches.length === 0;
  const watchFailed = (watchResults ?? []).filter((r) => !r.matched);

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
      이_런이_만들지_않은_주문: summary.orphanOrders.length,
    },
    latency: summary.latency,
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
    `  완주        ${summary.completed} (상한 ${Math.min(config.orderCount, summary.dreamiCount)} — 드리미 계정 수)`,
  );
  if (c.boormiRejected || c.offerError) push(`  기타        부르미거절 ${c.boormiRejected} · 오퍼오류 ${c.offerError}`);
  if (summary.closedViaError.length > 0) {
    push(`  패배 통지   offer_closed 대신 offer_error로 마감을 받은 오퍼 ${summary.closedViaError.length}건 (유실 아님)`);
  }
  if (summary.browserOrders.length > 0) {
    push(`  브라우저 주문 ${summary.browserOrders.length}건의 오퍼가 부하 드리미에게도 갔다 (정상)`);
  }
  if (summary.orphanOrders.length > 0) {
    push(`  ⚠ 이 런이 만들지 않은 주문의 이벤트 ${summary.orphanOrders.length}건 — 이전 런의 인메모리 잔여 상태를 의심하세요.`);
  }
  if (c.orphanEvents) push(`  ⚠ 원장에 붙지 못한 이벤트 ${c.orphanEvents}건`);
  push("");

  push("── 2. 단계별 지연 ──");
  for (const row of latencyRows(summary.latency)) {
    push(`  ${row.stage.padEnd(26, " ")} n=${String(row.count).padStart(4)}  p50 ${row.p50.padStart(7)}  p95 ${row.p95.padStart(7)}  p99 ${row.p99.padStart(7)}`);
  }
  push("");

  push("── 3. 드리미별 분포 ──");
  push(
    `  오퍼를 받은 계정 ${dist.받은계정}/${summary.dreamiCount} · 한 번도 못 받은 계정 ${dist.못받은계정}`,
    `  총 오퍼 ${dist.총오퍼} · 수락 제출 ${dist.수락제출} · 선착순 승 ${dist.선착순승} · 완주 ${dist.완주}`,
    `  최다 ${dist.최다} · 최소 ${dist.최소}`,
    "  ※ 오퍼 배정은 거리 무관 전역 FIFO(updatedAt 오름차순)라 분포는 균등하지 않다 — 현 구현의 성질이다.",
    "",
  );

  push("── 4. 부르미별 분포 ──");
  push(`  주문을 만든 계정 ${Object.keys(summary.byBoormi).length} · 매칭 미성사 주문을 가진 계정 ${unmatchedBoormis.length}`);
  if (summary.capacityBlocked.length > 0) {
    push(`  완주 상한에 막혀 오퍼조차 못 받은 주문 ${summary.capacityBlocked.length}건 (유실 아님)`);
  }
  push("");

  push("── 5. 유실 알람 ──");
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

  push("── 6. DB 대조 ──");
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

  push("── 7. 실클라이언트(Playwright) ──");
  if (!watchResults) {
    push("  실행하지 않음 (WATCH=0)");
  } else {
    for (const r of watchResults) {
      const mark = r.matched ? "✓" : "✗";
      push(
        `  ${mark} ${r.label} (${r.role}) ${r.stage}` +
          (r.elapsedMs !== null && r.elapsedMs !== undefined ? ` ${r.elapsedMs}ms` : "") +
          (r.error ? `  — ${r.error}` : ""),
      );
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

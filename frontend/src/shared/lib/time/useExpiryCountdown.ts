import { useEffect, useRef, useState } from "react";

/** 재계산 주기(ms). */
const TICK_MS = 1000;

export interface UseExpiryCountdownOptions {
  /** remainingSeconds가 0으로 떨어지는 순간 1회 호출. */
  onExpire?: () => void;
}

export interface ExpiryCountdownState {
  /** 남은 시간(초). 0 미만으로 내려가지 않는다. */
  remainingSeconds: number;
  /** 0~100 진행률(남은 비율). startedAt/expiresAt이 없거나 파싱 실패면 0. */
  progressPercent: number;
  /** remainingSeconds가 0인지. */
  expired: boolean;
}

const INACTIVE_STATE: ExpiryCountdownState = {
  remainingSeconds: 0,
  progressPercent: 0,
  expired: false,
};

function parseTime(iso: string | null | undefined): number | null {
  if (!iso) return null;
  const ms = new Date(iso).getTime();
  return Number.isNaN(ms) ? null : ms;
}

function computeState(startedMs: number, expiresMs: number, nowMs: number): ExpiryCountdownState {
  const remainingMs = Math.max(0, expiresMs - nowMs);
  const totalMs = expiresMs - startedMs;
  const progressPercent =
    totalMs > 0 ? Math.max(0, Math.min(100, (remainingMs / totalMs) * 100)) : 0;
  return {
    remainingSeconds: Math.ceil(remainingMs / 1000),
    progressPercent,
    expired: remainingMs <= 0,
  };
}

/**
 * 절대 시각(startedAt~expiresAt) 기반 카운트다운. 남은 초를 감소시키는 게 아니라
 * 매 tick `Date.now()`로 다시 계산해, 백그라운드 탭에서 interval이 쉬었어도 드리프트가 없다.
 *
 * - `document.visibilitychange`로 탭이 다시 보이면 즉시 1회 재계산해 보정한다.
 * - `remainingSeconds`가 0으로 처음 떨어지는 순간 `onExpire`를 1회만 호출한다.
 * - `startedAt`/`expiresAt`이 없거나 파싱 실패면 타이머를 돌리지 않고 비활성 상태를 반환한다
 *   (드리미/부르미 최초 SSE payload는 항상 값을 채워 보내므로 실사용에선 일어나지 않는 방어적 경로).
 * - `onExpire`는 매 렌더 새로 생기므로 ref로 최신본을 보관한다(`useDreamiLocationBroadcast`와 동일 패턴).
 */
export function useExpiryCountdown(
  startedAt: string | null | undefined,
  expiresAt: string | null | undefined,
  options: UseExpiryCountdownOptions = {},
): ExpiryCountdownState {
  const onExpireRef = useRef(options.onExpire);
  useEffect(() => {
    onExpireRef.current = options.onExpire;
  });

  const startedMs = parseTime(startedAt);
  const expiresMs = parseTime(expiresAt);
  const active = startedMs != null && expiresMs != null;

  const [tick, setTick] = useState<ExpiryCountdownState>(INACTIVE_STATE);
  const expiredFiredRef = useRef(false);

  useEffect(() => {
    if (startedMs == null || expiresMs == null) return;
    expiredFiredRef.current = false;

    const recompute = () => {
      const next = computeState(startedMs, expiresMs, Date.now());
      setTick(next);
      if (next.expired && !expiredFiredRef.current) {
        expiredFiredRef.current = true;
        onExpireRef.current?.();
      }
    };

    recompute();
    const timer = window.setInterval(recompute, TICK_MS);

    const onVisibilityChange = () => {
      if (document.visibilityState === "visible") recompute();
    };
    document.addEventListener("visibilitychange", onVisibilityChange);

    return () => {
      window.clearInterval(timer);
      document.removeEventListener("visibilitychange", onVisibilityChange);
    };
  }, [startedMs, expiresMs]);

  return active ? tick : INACTIVE_STATE;
}

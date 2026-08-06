import { useEffect, useRef, useState } from "react";
import { api } from "@/shared/api";

/** 드리미 위치 전송 주기(ms). 백엔드 권장 5~10초 중 5초. */
export const LOCATION_BROADCAST_INTERVAL_MS = 5000;

export interface UseDreamiLocationBroadcastOptions {
  /** false면 전송하지 않는다(예: 모의 모드). 기본 true. */
  enabled?: boolean;
  /** 전송 주기(ms). 기본 LOCATION_BROADCAST_INTERVAL_MS. */
  intervalMs?: number;
}

export interface DreamiLocationBroadcastState {
  /** geolocation 미지원/권한 거부 등 오류 메시지(정상 시 null). 선택적 UI 노출용. */
  error: string | null;
}

/**
 * 드리미(배달원) 현재 GPS 위치를 주기적으로 백엔드에 전송하는 훅.
 *
 * - `enabled && orderId`일 때만 동작한다. 마운트 즉시 `watchPosition`으로 권한 프롬프트를
 *   띄우고 최신 좌표를 ref에 유지한다(`enableHighAccuracy`). 렌더를 유발하지 않도록 state가 아닌 ref로 둔다.
 * - `intervalMs`마다 최신 좌표를 `POST .../dreami-location`으로 보낸다. 아직 fix가 없으면 그 tick은 건너뛰고,
 *   응답(`result`)은 사용하지 않는다(void 취급). 전송 실패는 조용히 로그만 남긴다.
 * - unmount/`enabled=false`/`orderId` 변경 시 `clearWatch` + `clearInterval`로 정리한다.
 * - geolocation 미지원/거부 시 폴백 없이 로그만 남기고, 오류는 반환값(`error`)으로 노출한다.
 *
 * 참고: `useSse`의 enabled 옵션·ref-최신화 패턴.
 */
export function useDreamiLocationBroadcast(
  orderId: string | null,
  options: UseDreamiLocationBroadcastOptions = {},
): DreamiLocationBroadcastState {
  const { enabled = true, intervalMs = LOCATION_BROADCAST_INTERVAL_MS } =
    options;
  // 권한 거부 등 런타임 오류만 state로 둔다(async 콜백에서만 갱신). 미지원 여부는 렌더 중 파생값으로 계산.
  const [permissionError, setPermissionError] = useState<string | null>(null);

  // 최신 fix를 보관(전송 tick이 읽어감). state가 아니라 ref라 좌표 갱신이 렌더를 유발하지 않는다.
  const lastFixRef = useRef<{ latitude: number; longitude: number } | null>(
    null,
  );

  const active = enabled && !!orderId;
  const supported =
    typeof navigator !== "undefined" && "geolocation" in navigator;

  useEffect(() => {
    // orderId를 직접 검사해 이후 코드에서 non-null로 좁힌다.
    if (!enabled || !orderId || !supported) {
      if (enabled && orderId && !supported) {
        console.warn("[dreami-location] geolocation 미지원");
      }
      return;
    }

    lastFixRef.current = null;

    // 마운트 즉시 watchPosition으로 권한 프롬프트를 강제하고 최신 좌표를 ref에 유지한다.
    const watchId = navigator.geolocation.watchPosition(
      (pos) => {
        setPermissionError(null);
        lastFixRef.current = {
          latitude: pos.coords.latitude,
          longitude: pos.coords.longitude,
        };
      },
      (err) => {
        setPermissionError(err.message || "위치 권한이 거부됐어요.");
        console.warn("[dreami-location] watchPosition 오류:", err.message);
      },
      { enableHighAccuracy: true },
    );

    // 주기적으로 최신 좌표를 전송한다. 아직 fix가 없으면 그 tick은 건너뛴다.
    const timer = window.setInterval(() => {
      const fix = lastFixRef.current;
      if (!fix) return;
      api.updateDreamiLocation(orderId, fix).catch((e) => {
        console.warn("[dreami-location] 전송 실패:", e);
      });
    }, intervalMs);

    return () => {
      navigator.geolocation.clearWatch(watchId);
      clearInterval(timer);
    };
  }, [enabled, orderId, supported, intervalMs]);

  // 미지원은 파생값으로, 권한 오류는 state로 합쳐 노출한다.
  const error =
    active && !supported
      ? "이 브라우저는 위치 기능을 지원하지 않아요."
      : permissionError;

  return { error };
}

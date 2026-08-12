import { useEffect, useRef, useState } from "react";
import { api, type DreamiLocationResponseDto } from "@/shared/api";

/** 드리미 위치 전송 주기(ms). 백엔드 권장 5~10초 중 5초. */
export const LOCATION_BROADCAST_INTERVAL_MS = 5000;

/**
 * 이 시간(ms)보다 오래된 좌표는 더 이상 전송하지 않는다.
 *
 * 새 fix가 끊긴 뒤에도 마지막 좌표를 계속 보내면, 서버는 위치가 정상 수신되는 것으로 보고
 * 부르미에게는 "실시간인데 움직이지 않는 마커"가 남는다. 오래된 좌표를 보내지 않으면 서버의
 * 무소식 감지가 작동해 부르미가 안내를 받는다. GPS 권한 철회 시 에러 콜백이 뜨지 않는 브라우저나
 * 지하·터널처럼 신호만 끊기는 경우까지, 에러 이벤트에 의존하지 않고 덮는다.
 */
export const STALE_FIX_MS = 15000; // 예를 들어 15초보다 오래된 좌표는 서버로 안보냄

export interface UseDreamiLocationBroadcastOptions {
  /** false면 전송하지 않는다(예: 모의 모드). 기본 true. */
  enabled?: boolean;
  /** 전송 주기(ms). 기본 LOCATION_BROADCAST_INTERVAL_MS. */
  intervalMs?: number;
  /**
   * true면 서버가 응답에 '드리미→픽업지' 경로·배송완료예상시간을 담아 준다. 아직 값이 없는 동안 true로 보내고,
   * 한 번 받으면 false로 바꿔 좌표 배열을 매 전송마다 중복 수신하지 않게 한다. 기본 false.
   */
  includeRoute?: boolean;
  /**
   * 위치 전송이 성공할 때마다 서버 응답(드리미→픽업지 경로·배송완료예상시간)을 전달한다.
   * includeRoute=true인 동안 이 콜백으로 값을 받아 화면을 갱신하면 별도 재조회가 필요 없다.
   */
  onResult?: (result: DreamiLocationResponseDto | undefined) => void;
}

export interface DreamiLocationBroadcastState {
  /** geolocation 미지원/권한 거부 등 오류 메시지(정상 시 null). 선택적 UI 노출용. */
  error: string | null;
  /** 최신 GPS 좌표(fix 이전엔 null). 화면 지도 표시용. */
  position: { latitude: number; longitude: number } | null;
}

/**
 * 드리미(배달원) 현재 GPS 위치를 주기적으로 백엔드에 전송하는 훅.
 * !! 브라우저가 GPS 위치 값 측정하는 것도 이 함수에서 수행됨
 *
 * - `enabled && orderId`일 때만 동작한다. 마운트 즉시 `watchPosition`으로 권한 프롬프트를
 *   띄우고 최신 좌표를 유지한다(`enableHighAccuracy`). 전송 tick은 stale closure를 피하려 ref를 읽고,
 *   화면 지도 표시용으로는 같은 좌표를 state(`position`)로도 노출한다.
 * - 첫 fix는 즉시 `POST .../dreami-location`으로 보내고, 그 다음부터 `intervalMs`마다 최신 좌표를 보낸다.
 *   응답(`result`)은 `onResult`로 전달하고, 전송 실패는 조용히 로그만 남긴다.
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
  // 전송 tick(interval)은 stale closure를 피하려 ref로 최신 콜백·플래그를 읽는다(useSse의 ref-최신화 패턴과 동일).
  const onResultRef = useRef(options.onResult);
  const includeRouteRef = useRef(options.includeRoute);
  useEffect(() => {
    onResultRef.current = options.onResult;
    includeRouteRef.current = options.includeRoute;
  });
  // 권한 거부 등 런타임 오류만 state로 둔다(async 콜백에서만 갱신). 미지원 여부는 렌더 중 파생값으로 계산.
  const [permissionError, setPermissionError] = useState<string | null>(null);
  // 화면 지도 표시용 최신 좌표(state). 전송 tick은 아래 ref를 읽는다.
  const [position, setPosition] = useState<{
    latitude: number;
    longitude: number;
  } | null>(null);

  // 최신 fix를 보관(전송 tick이 읽어감). ref라 stale closure 없이 최신값을 읽는다.
  const lastFixRef = useRef<{ latitude: number; longitude: number } | null>(
    null,
  );
  // 그 fix가 도착한 시각. 오래된 좌표를 실시간인 것처럼 계속 보내지 않기 위해 함께 기록한다.
  const lastFixAtRef = useRef(0);

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
    lastFixAtRef.current = 0;

    let sentInitialFix = false;
    let requestInFlight = false;
    let timer: number | null = null;

    const sendLocation = (fix: { latitude: number; longitude: number }) => {
      if (requestInFlight) return;
      requestInFlight = true;
      api
        .updateDreamiLocation(orderId, {
          ...fix,
          includeRoute: includeRouteRef.current ?? false,
        })
        .then((res) => onResultRef.current?.(res.result))
        .catch((e) => {
          console.warn("[dreami-location] 전송 실패:", e);
        })
        .finally(() => {
          requestInFlight = false;
        });
    };

    // 마운트 즉시 watchPosition으로 권한 프롬프트를 강제하고 최신 좌표를 ref에 유지한다.
    const watchId = navigator.geolocation.watchPosition(
      (pos) => {
        setPermissionError(null);
        const fix = {
          latitude: pos.coords.latitude,
          longitude: pos.coords.longitude,
        };
        lastFixRef.current = fix; // 최신 값 보관 (전송용)
        lastFixAtRef.current = Date.now();
        setPosition(fix); // 드리미가 사용할 position값 갱신

        // 첫 GPS 좌표는 타이머를 기다리지 않고 즉시 보낸 뒤, 이 시점부터 주기 전송을 시작한다.
        if (!sentInitialFix) {
          sentInitialFix = true;
          sendLocation(fix);
          timer = window.setInterval(() => {
            const latestFix = lastFixRef.current;
            // 새 fix가 끊긴 뒤 마지막 좌표를 계속 보내면 서버가 정상 수신으로 오해한다 → 오래된 좌표는 보내지 않는다.
            if (!latestFix) return;
            if (Date.now() - lastFixAtRef.current > STALE_FIX_MS) {
              // 일부 브라우저는 기기 GPS가 꺼져도 error 콜백을 주지 않는다. 서버 응답과 무관하게
              // 마지막 fix 시각으로 중단을 감지해 드리미 화면이 위치 허용 안내를 띄울 수 있게 한다.
              setPermissionError("GPS 위치를 확인할 수 없어요.");
              return;
            }
            sendLocation(latestFix);
          }, intervalMs);
        }
      },
      (err) => {
        setPermissionError(err.message || "위치 권한이 거부됐어요.");
        console.warn("[dreami-location] watchPosition 오류:", err.message);
        // 권한 거부는 영구적이므로 위 stale 가드(최대 15초)를 기다리지 않고 즉시 전송을 끊는다.
        // clearInterval은 하지 않는다 — tick이 no-op이 되고, 권한이 다시 허용되면 성공 콜백이
        // lastFixRef를 채워 전송이 자연히 재개된다.
        if (err.code === err.PERMISSION_DENIED) {
          lastFixRef.current = null;
        }
      },
      { enableHighAccuracy: true },
    );

    // 화면을 벗어나면 GPS 감시와 주기 전송을 함께 중단한다.
    return () => {
      navigator.geolocation.clearWatch(watchId);
      if (timer !== null) clearInterval(timer);
    };
  }, [enabled, orderId, supported, intervalMs]);

  // 미지원은 파생값으로, 권한 오류는 state로 합쳐 노출한다.
  const error =
    active && !supported
      ? "이 브라우저는 위치 기능을 지원하지 않아요."
      : permissionError;

  return { error, position };
}

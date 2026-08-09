import { useEffect, useRef, useState } from "react";
import { loadKakaoMaps } from "@/shared/lib";
import { cn } from "@/shared/lib/cn";
import { pinImage } from "../DeliveryRouteMap/pinImage";
import type { Coords } from "../DeliveryRouteMap/DeliveryRouteMap";

export interface NearbyCall {
  /** 마커 key·클릭 콜백 식별자로 쓴다. */
  id: string;
  location: Coords;
  itemName?: string;
  expectedRevenue?: number;
  expectedEtaMinutes?: number;
  distanceMeters?: number;
}

export interface NearbyCallsMapProps {
  /** 내 현재 좌표(없으면 지도를 그리지 않고 대기 문구를 보여준다). */
  center: Coords | null;
  calls: NearbyCall[];
  /** 콜 핀 클릭 시 해당 콜 정보를 전달한다. */
  onCallClick?: (call: NearbyCall) => void;
  /** 위치 조회 실패 등 대체 문구(center가 없을 때만 노출). */
  fallbackMessage?: string | null;
  height?: number;
  flat?: boolean;
}

const MY_LOCATION_COLOR = "#0d1b3d"; // navy-900
const CALL_COLOR = "#00b7a7"; // teal-500

/**
 * 내 위치 핀 1개 + 주변 콜 핀 N개를 보여주는 지도. goOnline 여부와 무관하게
 * center·calls만 있으면 그려진다. 콜 핀을 클릭하면 onCallClick으로 상세 정보를 전달한다.
 * VITE_KAKAO_MAP_KEY가 없거나 center가 없으면 대체 문구로 폴백한다.
 */
export function NearbyCallsMap({
  center,
  calls,
  onCallClick,
  fallbackMessage,
  height = 280,
  flat = false,
}: NearbyCallsMapProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const kakaoRef = useRef<typeof window.kakao | null>(null);
  const mapRef = useRef<ReturnType<typeof window.kakao.maps.Map> | null>(null);
  const myMarkerRef = useRef<unknown>(null);
  const callMarkersRef = useRef<Map<string, unknown>>(new Map());
  const [status, setStatus] = useState<"loading" | "ready" | "disabled">(
    "loading",
  );

  // 지도는 1회만 생성한다.
  useEffect(() => {
    let cancelled = false;
    loadKakaoMaps()
      .then((kakao) => {
        if (cancelled) return;
        if (!kakao || !containerRef.current) {
          setStatus("disabled");
          return;
        }
        kakaoRef.current = kakao;
        mapRef.current = new kakao.maps.Map(containerRef.current, {
          center: new kakao.maps.LatLng(37.5665, 126.978),
          level: 4,
        });
        setStatus("ready");
      })
      .catch(() => {
        if (!cancelled) setStatus("disabled");
      });

    return () => {
      cancelled = true;
      kakaoRef.current = null;
      mapRef.current = null;
      myMarkerRef.current = null;
      callMarkersRef.current = new Map();
    };
  }, []);

  // 내 위치 핀: 생성/이동 + 중심 이동.
  useEffect(() => {
    const kakao = kakaoRef.current;
    const map = mapRef.current;
    if (status !== "ready" || !kakao || !map || !center) return;
    const pos = new kakao.maps.LatLng(center.latitude, center.longitude);
    if (myMarkerRef.current) {
      (myMarkerRef.current as ReturnType<typeof kakao.maps.Marker>).setPosition(
        pos,
      );
    } else {
      myMarkerRef.current = new kakao.maps.Marker({
        map,
        position: pos,
        image: pinImage(kakao, MY_LOCATION_COLOR),
      });
      map.setCenter(pos);
    }
  }, [status, center]);

  // 콜 핀: calls 목록에 맞춰 마커를 새로 그린다(사라진 콜의 마커는 지운다).
  useEffect(() => {
    const kakao = kakaoRef.current;
    const map = mapRef.current;
    if (status !== "ready" || !kakao || !map) return;

    const next = new Map<string, unknown>();
    calls.forEach((call) => {
      const pos = new kakao.maps.LatLng(
        call.location.latitude,
        call.location.longitude,
      );
      const existing = callMarkersRef.current.get(call.id);
      if (existing) {
        (existing as ReturnType<typeof kakao.maps.Marker>).setPosition(pos);
        next.set(call.id, existing);
        return;
      }
      const marker = new kakao.maps.Marker({
        map,
        position: pos,
        image: pinImage(kakao, CALL_COLOR),
      });
      kakao.maps.event.addListener(marker, "click", () => onCallClick?.(call));
      next.set(call.id, marker);
    });

    // 이번 목록에 없는 기존 마커는 지도에서 제거한다.
    callMarkersRef.current.forEach((marker, id) => {
      if (!next.has(id)) {
        (marker as ReturnType<typeof kakao.maps.Marker>).setMap(null);
      }
    });
    callMarkersRef.current = next;
  }, [status, calls, onCallClick]);

  if (status === "disabled") {
    return (
      <div
        className={cn(
          "flex items-center justify-center bg-canvas text-center text-2xs leading-[14px] text-muted",
          !flat && "rounded-md border border-dashed border-line",
        )}
        style={{ height }}
      >
        <p>
          지도 / MAP
          <br />
          {fallbackMessage ?? "지도 서비스를 사용할 수 없어요."}
        </p>
      </div>
    );
  }

  return (
    <div
      className={cn(
        "relative isolate w-full overflow-hidden",
        !flat && "rounded-md border border-line",
      )}
      style={{ height }}
    >
      {/* center 유무와 무관하게 컨테이너는 항상 렌더링한다 — loadKakaoMaps가 끝나는 시점에
          containerRef가 비어있으면 지도를 영구히 disabled로 확정시키기 때문. */}
      <div ref={containerRef} className="h-full w-full" />
      {status === "loading" && (
        <div className="absolute inset-0 flex items-center justify-center bg-canvas text-2xs text-muted">
          지도 불러오는 중…
        </div>
      )}
      {status === "ready" && !center && (
        <div className="absolute inset-0 flex items-center justify-center bg-canvas text-2xs text-muted">
          {fallbackMessage ?? "위치를 확인하고 있어요…"}
        </div>
      )}
    </div>
  );
}

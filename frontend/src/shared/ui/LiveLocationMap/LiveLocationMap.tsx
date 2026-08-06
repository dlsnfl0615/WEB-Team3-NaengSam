import { useEffect, useRef, useState } from "react";
import { loadKakaoMaps } from "@/shared/lib";
import { cn } from "@/shared/lib/cn";

export interface LiveLocationMapProps {
  /** 마커 위도. 없으면 폴백 문구를 표시한다. */
  latitude?: number;
  /** 마커 경도. 없으면 폴백 문구를 표시한다. */
  longitude?: number;
  /** 지도 높이(px). 기본 340. */
  height?: number;
  /** 모서리 반경·테두리 제거(풀블리드 지도용, MapCard `flat`과 함께). */
  flat?: boolean;
}

/** 마커 색(theme.css teal-500 토큰 hex 재사용). */
const MARKER_COLOR = "#00b7a7"; // teal-500

/** teal 핀 모양 MarkerImage를 만든다(카카오 마커는 이미지 URI 필요). */
function pinImage(kakao: typeof window.kakao) {
  const svg =
    `<svg xmlns="http://www.w3.org/2000/svg" width="30" height="40" viewBox="0 0 30 40">` +
    `<path d="M15 0C6.716 0 0 6.716 0 15c0 10.5 15 25 15 25s15-14.5 15-25C30 6.716 23.284 0 15 0z" fill="${MARKER_COLOR}"/>` +
    `<circle cx="15" cy="15" r="5.5" fill="#fff"/></svg>`;
  return new kakao.maps.MarkerImage(
    `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`,
    new kakao.maps.Size(30, 40),
    { offset: new kakao.maps.Point(15, 40) },
  );
}

/**
 * 단일 마커 실시간 지도. 좌표가 바뀔 때마다 마커를 그 위치로 옮긴다(보간 없음).
 * VITE_KAKAO_MAP_KEY가 없거나 좌표가 아직 없으면 좌표 텍스트로 폴백한다.
 */
export function LiveLocationMap({
  latitude,
  longitude,
  height = 340,
  flat = false,
}: LiveLocationMapProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const mapRef = useRef<ReturnType<typeof window.kakao.maps.Map> | null>(null);
  const markerRef = useRef<ReturnType<typeof window.kakao.maps.Marker> | null>(
    null,
  );
  const [status, setStatus] = useState<"loading" | "ready" | "disabled">(
    "loading",
  );

  const hasCoords = latitude != null && longitude != null;

  // 지도·마커는 1회만 생성한다.
  useEffect(() => {
    let cancelled = false;
    loadKakaoMaps()
      .then((kakao) => {
        if (cancelled) return;
        if (!kakao || !containerRef.current) {
          setStatus("disabled");
          return;
        }
        const center = new kakao.maps.LatLng(
          latitude ?? 37.5665,
          longitude ?? 126.978, // 좌표 없으면 서울시청 기본값
        );
        const map = new kakao.maps.Map(containerRef.current, {
          center,
          level: 4,
        });
        const marker = new kakao.maps.Marker({
          map,
          position: center,
          image: pinImage(kakao),
        });
        mapRef.current = map;
        markerRef.current = marker;
        setStatus("ready");
      })
      .catch(() => {
        if (!cancelled) setStatus("disabled");
      });

    return () => {
      cancelled = true;
      mapRef.current = null;
      markerRef.current = null;
    };
    // 최초 1회만 생성한다(좌표 변화는 아래 effect에서 마커만 이동).
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []); // 의존성 배열을 []로 하여 1번만 초기화 되도록

  // 좌표가 바뀌면 마커·중심을 이동한다.
  useEffect(() => {
    const map = mapRef.current;
    const marker = markerRef.current;
    if (!map || !marker || !hasCoords) return;
    const pos = new window.kakao.maps.LatLng(
      latitude as number,
      longitude as number,
    );
    marker.setPosition(pos);
    map.setCenter(pos);
  }, [latitude, longitude, hasCoords, status]);

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
          {hasCoords
            ? `${latitude!.toFixed(5)}, ${longitude!.toFixed(5)}`
            : "위치 대기 중"}
        </p>
      </div>
    );
  }

  return (
    <div
      className={cn(
        "relative w-full overflow-hidden",
        !flat && "rounded-md border border-line",
      )}
      style={{ height }}
    >
      <div ref={containerRef} className="h-full w-full" />
      {status === "loading" && (
        <div className="absolute inset-0 flex items-center justify-center bg-canvas text-2xs text-muted">
          지도 불러오는 중…
        </div>
      )}
    </div>
  );
}

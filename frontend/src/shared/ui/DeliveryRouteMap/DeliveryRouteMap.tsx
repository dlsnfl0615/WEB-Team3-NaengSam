import { useEffect, useRef, useState } from "react";
import { loadKakaoMaps } from "@/shared/lib";
import { cn } from "@/shared/lib/cn";
import { pinImage } from "./pinImage";

/** 위·경도 좌표쌍. */
export interface Coords {
  latitude: number;
  longitude: number;
}

export interface DeliveryRouteMapProps {
  /** 출발지 좌표(없으면 핀 생략). */
  pickup?: Coords;
  /** 도착지 좌표(없으면 핀 생략). */
  dropoff?: Coords;
  /** 드리미(배달원) 현재 좌표. 바뀔 때마다 핀이 이동한다. */
  driver?: Coords;
  /** 드리미 핀 라벨. 기본값은 "드리미". (내 위치 등으로 사용될 수 있음)*/
  driverLabel?: string;
  /** 지도 높이(px). 기본 340. */
  height?: number;
  /** 모서리 반경·테두리 제거(풀블리드 지도용, MapCard `flat`과 함께). */
  flat?: boolean;
}

type Role = "pickup" | "dropoff" | "driver";

/** 역할별 핀 색(theme.css 토큰 hex 재사용)·라벨 텍스트·라벨 배경(토큰 유틸). */
const ROLE: Record<Role, { color: string; label: string; bg: string }> = {
  pickup: { color: "#0d1b3d", label: "출발지", bg: "bg-navy-900" }, // navy-900
  dropoff: { color: "#00b7a7", label: "도착지", bg: "bg-teal-500" }, // teal-500
  driver: { color: "#b26a00", label: "드리미", bg: "bg-status-warning" }, // status-warning
};

/** 핀 위에 뜨는 작은 역할 라벨 CustomOverlay를 만든다(핀 높이 40px 위로 띄움). */
function makeLabel(
  kakao: typeof window.kakao,
  position: ReturnType<typeof window.kakao.maps.LatLng>,
  role: Role,
  label: string, // 핀 위에 뜨는 라벨 문자열
) {
  const content = document.createElement("div");
  content.className = cn(
    "rounded-pill px-1.5 py-0.5 text-2xs font-semibold text-white shadow-card",
    ROLE[role].bg,
  );
  content.textContent = label;
  content.style.transform = "translateY(-46px)"; // 핀(40px) 위로 라벨을 올린다
  return new kakao.maps.CustomOverlay({
    position,
    content,
    xAnchor: 0.5,
    yAnchor: 0.5,
  });
}

/** 마커+라벨을 처음이면 생성하고, 이미 있으면 위치만 옮긴다. */
function upsertMarker(
  kakao: typeof window.kakao,
  map: ReturnType<typeof window.kakao.maps.Map>,
  store: { marker?: unknown; overlay?: unknown },
  role: Role,
  coords: Coords,
  label: string,
) {
  const pos = new kakao.maps.LatLng(coords.latitude, coords.longitude);
  if (store.marker) {
    (store.marker as ReturnType<typeof kakao.maps.Marker>).setPosition(pos);
    (store.overlay as ReturnType<typeof kakao.maps.CustomOverlay>)?.setPosition(
      pos,
    );
    return;
  }
  store.marker = new kakao.maps.Marker({
    map,
    position: pos,
    image: pinImage(kakao, ROLE[role].color),
  });
  const overlay = makeLabel(kakao, pos, role, label);
  overlay.setMap(map);
  store.overlay = overlay;
}

/**
 * 출발지·도착지·드리미 세 핀(라벨 포함)을 좌표로 표시하는 추적 지도. 지오코딩하지 않고 좌표만 받는다.
 * pickup/dropoff는 정적 핀(변경 시 갱신), driver는 좌표가 바뀔 때마다 이동한다(보간 없음).
 * VITE_KAKAO_MAP_KEY가 없으면 좌표 텍스트로 폴백한다.
 */
export function DeliveryRouteMap({
  pickup,
  dropoff,
  driver,
  driverLabel = "드리미", // 기본 값은 "드리미"
  height = 340,
  flat = false,
}: DeliveryRouteMapProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const kakaoRef = useRef<typeof window.kakao | null>(null);
  const mapRef = useRef<ReturnType<typeof window.kakao.maps.Map> | null>(null);
  const storeRef = useRef<Record<Role, { marker?: unknown; overlay?: unknown }>>(
    { pickup: {}, dropoff: {}, driver: {} },
  );
  const didFitRef = useRef(false);
  const [status, setStatus] = useState<"loading" | "ready" | "disabled">(
    "loading",
  );

  // 지도는 1회만 생성한다(서울시청 기본 중심, 뷰포트는 아래 fit effect가 맞춘다).
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
          level: 5,
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
      storeRef.current = { pickup: {}, dropoff: {}, driver: {} };
      didFitRef.current = false;
    };
  }, []);

  // 각 역할 핀을 좌표에 맞춰 생성/이동한다.
  useEffect(() => {
    const kakao = kakaoRef.current;
    const map = mapRef.current;
    if (status !== "ready" || !kakao || !map) return;
    const entries: [Role, Coords | undefined][] = [
      ["pickup", pickup],
      ["dropoff", dropoff],
      ["driver", driver],
    ];
    entries.forEach(([role, coords]) => {
      if (coords) {
        const label = role === "driver" ? driverLabel : ROLE[role].label;
        upsertMarker(kakao, map, storeRef.current[role], role, coords, label);
      }
    });
  }, [status, pickup, dropoff, driver, driverLabel]);

  // 존재하는 핀들로 최초 1회만 뷰포트를 맞춘다(이후 드리미 이동 시 강제 recenter 안 함 → 튐 방지).
  useEffect(() => {
    const kakao = kakaoRef.current;
    const map = mapRef.current;
    if (status !== "ready" || !kakao || !map || didFitRef.current) return;
    const present = [pickup, dropoff, driver].filter(
      (c): c is Coords => c != null,
    );
    if (present.length === 0) return;
    if (present.length === 1) {
      map.setCenter(
        new kakao.maps.LatLng(present[0].latitude, present[0].longitude),
      );
    } else {
      const bounds = new kakao.maps.LatLngBounds();
      present.forEach((c) =>
        bounds.extend(new kakao.maps.LatLng(c.latitude, c.longitude)),
      );
      map.setBounds(bounds);
    }
    didFitRef.current = true;
  }, [status, pickup, dropoff, driver]);

  if (status === "disabled") {
    const present: [string, Coords][] = [
      ["출발지", pickup],
      ["도착지", dropoff],
      [driverLabel, driver],
    ].filter((e): e is [string, Coords] => e[1] != null);
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
          {present.length > 0 ? (
            present.map(([label, c]) => (
              <span key={label}>
                <br />
                {label} {c.latitude.toFixed(5)}, {c.longitude.toFixed(5)}
              </span>
            ))
          ) : (
            <>
              <br />
              위치 대기 중
            </>
          )}
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
      <div ref={containerRef} className="h-full w-full" />
      {status === "loading" && (
        <div className="absolute inset-0 flex items-center justify-center bg-canvas text-2xs text-muted">
          지도 불러오는 중…
        </div>
      )}
    </div>
  );
}

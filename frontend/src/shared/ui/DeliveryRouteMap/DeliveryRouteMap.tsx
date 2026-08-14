import { useEffect, useRef, useState } from "react";
import { DELIVERY_MAP_SMOOTH_MODE } from "@/shared/config";
import { loadKakaoMaps } from "@/shared/lib";
import { cn } from "@/shared/lib/cn";
import { planDriverMotion } from "./driverMotion";
import { makePinOverlay } from "./pinOverlay";
import type { KakaoMap, PinOverlayHandle, PinStyle } from "./pinOverlay";

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
  /** 드리미 핀에 사용할 캐릭터 이미지 URL. 없으면 기본 핀 SVG를 사용한다. */
  driverPinImage?: string;
  /** 픽업 핀 라벨. 기본값은 "픽업 장소"(주문의 item_name을 넣으면 그 이름으로 표시). */
  pickupLabel?: string;
  /** 추천 이동경로 좌표 목록. 있으면 폴리라인으로 그린다(첫 로드부터 표시). */
  route?: Coords[];
  /** 지도 높이(px). 기본 340. */
  height?: number;
  /** 모서리 반경·테두리 제거(풀블리드 지도용, MapCard `flat`과 함께). */
  flat?: boolean;
}

type Role = "pickup" | "dropoff" | "driver";

interface DriverMotionState {
  target?: Coords;
  targetReceivedAt?: number;
  averageSpeedMps?: number;
}

/** 역할별 핀 색(theme.css 토큰 hex 재사용)·라벨 텍스트·라벨 배경(토큰 유틸). */
const ROLE: Record<Role, PinStyle> = {
  pickup: { color: "#0d1b3d", label: "픽업 장소", bg: "bg-navy-900" }, // navy-900
  dropoff: { color: "#b26a00", label: "도착지", bg: "bg-status-warning" }, // status-warning
  driver: { color: "#00b7a7", label: "드리미", bg: "bg-teal-500" }, // teal-500
};

/** 핀 오버레이를 처음이면 생성하고, 이미 있으면 위치만 옮긴다. */
function upsertMarker(
  kakao: typeof window.kakao,
  map: KakaoMap,
  store: { overlay?: PinOverlayHandle },
  role: Role,
  coords: Coords,
  label: string,
  smooth = false,
  imageSrc?: string,
) {
  const pos = new kakao.maps.LatLng(coords.latitude, coords.longitude);
  if (store.overlay) {
    if (role === "driver") store.overlay.setImageSrc(imageSrc);
    store.overlay.setPosition(pos);
    return;
  }
  store.overlay = makePinOverlay(
    kakao,
    map,
    pos,
    { ...ROLE[role], label, imageSrc },
    undefined,
    smooth,
  );
}

/** 드리미 핀을 처음이면 생성하고, 이미 있으면 픽셀 기반 애니메이션으로 옮긴다. */
function upsertSmoothDriver(
  kakao: typeof window.kakao,
  map: KakaoMap,
  store: { overlay?: PinOverlayHandle },
  coords: Coords,
  label: string,
  imageSrc?: string,
  durationMs?: number,
) {
  const position = new kakao.maps.LatLng(coords.latitude, coords.longitude);
  store.overlay?.setImageSrc(imageSrc);
  if (store.overlay && durationMs != null) {
    store.overlay.setSmoothPosition(position, durationMs);
    return;
  }
  if (store.overlay) {
    store.overlay.setPosition(position);
    return;
  }
  store.overlay = makePinOverlay(
    kakao,
    map,
    position,
    { ...ROLE.driver, label, imageSrc },
    undefined,
    true,
  );
}

/**
 * 출발지·도착지·드리미 세 핀(라벨 포함)을 좌표로 표시하는 추적 지도. 지오코딩하지 않고 좌표만 받는다.
 * pickup/dropoff는 정적 핀이고, driver는 공통 설정에 따라 즉시 이동하거나 픽셀 기반으로 부드럽게 이동한다.
 * VITE_KAKAO_MAP_KEY가 없으면 좌표 텍스트로 폴백한다.
 */
export function DeliveryRouteMap({
  pickup,
  dropoff,
  driver,
  driverLabel = "드리미", // 기본 값은 "드리미"
  driverPinImage,
  pickupLabel,
  route,
  height = 340,
  flat = false,
}: DeliveryRouteMapProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const kakaoRef = useRef<typeof window.kakao | null>(null);
  const mapRef = useRef<KakaoMap | null>(null);
  const storeRef = useRef<Record<Role, { overlay?: PinOverlayHandle }>>({
    pickup: {},
    dropoff: {},
    driver: {},
  });
  const routeLineRef = useRef<unknown>(null);
  const didFitRef = useRef(false);
  const driverMotionRef = useRef<DriverMotionState>({});
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
      Object.values(storeRef.current).forEach(({ overlay }) =>
        overlay?.setMap(null),
      );
      kakaoRef.current = null;
      mapRef.current = null;
      storeRef.current = { pickup: {}, dropoff: {}, driver: {} };
      routeLineRef.current = null;
      didFitRef.current = false;
      driverMotionRef.current = {};
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
    ];
    if (!DELIVERY_MAP_SMOOTH_MODE) entries.push(["driver", driver]);
    entries.forEach(([role, coords]) => {
      if (coords) {
        const label =
          role === "driver"
            ? driverLabel
            : role === "pickup"
              ? pickupLabel ?? ROLE.pickup.label
              : ROLE[role].label;
        upsertMarker(
          kakao,
          map,
          storeRef.current[role],
          role,
          coords,
          label,
          false,
          role === "driver" ? driverPinImage : undefined,
        );
      }
    });
  }, [
    status,
    pickup,
    dropoff,
    driver,
    driverLabel,
    driverPinImage,
    pickupLabel,
  ]);

  // SMOOTH 모드에서는 원본 좌표와 분리된 표시 좌표만 보간한다. 서버 전송·SSE 좌표는 건드리지 않는다.
  useEffect(() => {
    const motion = driverMotionRef.current;
    if (!DELIVERY_MAP_SMOOTH_MODE) return;
    if (!driver) return;
    const kakao = kakaoRef.current;
    const map = mapRef.current;
    if (status !== "ready" || !kakao || !map) return;

    // 픽업 완료처럼 좌표는 그대로이고 캐릭터 이미지만 바뀌는 경우에도 즉시 반영한다.
    // 이동 계획이 없으면 아래에서 조기 반환하므로 이미지 갱신은 그보다 먼저 해야 한다.
    storeRef.current.driver.overlay?.setImageSrc(driverPinImage);

    const receivedAt = performance.now();
    if (!motion.target || motion.targetReceivedAt == null) {
      motion.target = driver;
      motion.targetReceivedAt = receivedAt;
      upsertSmoothDriver(
        kakao,
        map,
        storeRef.current.driver,
        driver,
        driverLabel,
        driverPinImage,
      );
      return;
    }

    const plan = planDriverMotion({
      previousTarget: motion.target,
      previousReceivedAt: motion.targetReceivedAt,
      previousAverageSpeedMps: motion.averageSpeedMps,
      nextTarget: driver,
      receivedAt,
    });
    if (!plan) return;

    motion.target = driver;
    motion.targetReceivedAt = receivedAt;
    motion.averageSpeedMps = plan.averageSpeedMps;
    upsertSmoothDriver(
      kakao,
      map,
      storeRef.current.driver,
      driver,
      driverLabel,
      driverPinImage,
      plan.durationMs,
    );
  }, [status, driver, driverLabel, driverPinImage]);

  // 추천 이동경로를 폴리라인으로 그린다(좌표가 바뀌면 다시 그림). 핀처럼 ref에 보관해 중복 생성을 막는다.
  useEffect(() => {
    const kakao = kakaoRef.current;
    const map = mapRef.current;
    if (status !== "ready" || !kakao || !map) return;
    if (routeLineRef.current) {
      (routeLineRef.current as ReturnType<typeof kakao.maps.Polyline>).setMap(
        null,
      );
      routeLineRef.current = null;
    }
    if (!route || route.length < 2) return;
    routeLineRef.current = new kakao.maps.Polyline({
      map,
      path: route.map((c) => new kakao.maps.LatLng(c.latitude, c.longitude)),
      strokeWeight: 5,
      strokeColor: "#00b7a7", // teal-500
      strokeOpacity: 0.9,
      strokeStyle: "solid",
    });
  }, [status, route]);

  // 존재하는 핀들·경로로 최초 1회만 뷰포트를 맞춘다(이후 드리미 이동 시 강제 recenter 안 함 → 튐 방지).
  useEffect(() => {
    const kakao = kakaoRef.current;
    const map = mapRef.current;
    if (status !== "ready" || !kakao || !map || didFitRef.current) return;
    const present = [pickup, dropoff, driver, ...(route ?? [])].filter(
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
  }, [status, pickup, dropoff, driver, route]);

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

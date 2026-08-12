import { useEffect, useRef, useState } from "react";
import { DELIVERY_MAP_SMOOTH_MODE } from "@/shared/config";
import { loadKakaoMaps } from "@/shared/lib";
import { cn } from "@/shared/lib/cn";
import {
  interpolatePoint,
  planDriverMotion,
  type PixelPoint,
} from "./driverMotion";
import { pinImageSrc } from "./pinImage";

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
  /** 추천 이동경로 좌표 목록. 있으면 폴리라인으로 그린다(첫 로드부터 표시). */
  route?: Coords[];
  /** 지도 높이(px). 기본 340. */
  height?: number;
  /** 모서리 반경·테두리 제거(풀블리드 지도용, MapCard `flat`과 함께). */
  flat?: boolean;
}

type Role = "pickup" | "dropoff" | "driver";
type KakaoMap = ReturnType<typeof window.kakao.maps.Map>;
type KakaoLatLng = ReturnType<typeof window.kakao.maps.LatLng>;

interface DriverMotionState {
  target?: Coords;
  targetReceivedAt?: number;
  averageSpeedMps?: number;
}

interface DeliveryPinOverlay {
  setMap(map: KakaoMap | null): void;
  setPosition(position: KakaoLatLng): void;
  setSmoothPosition(position: KakaoLatLng, durationMs: number): void;
}

interface PinAnimation {
  fromPosition: KakaoLatLng;
  toPosition: KakaoLatLng;
  fromPoint: PixelPoint;
  toPoint: PixelPoint;
  startedAt: number;
  durationMs: number;
  frame?: number;
}

/** 역할별 핀 색(theme.css 토큰 hex 재사용)·라벨 텍스트·라벨 배경(토큰 유틸). */
const ROLE: Record<Role, { color: string; label: string; bg: string }> = {
  pickup: { color: "#0d1b3d", label: "픽업 장소", bg: "bg-navy-900" }, // navy-900
  dropoff: { color: "#00b7a7", label: "도착지", bg: "bg-teal-500" }, // teal-500
  driver: { color: "#b26a00", label: "드리미", bg: "bg-status-warning" }, // status-warning
};

/** 핀과 역할 라벨을 하나의 AbstractOverlay로 만든다. */
function makePinOverlay(
  kakao: typeof window.kakao,
  map: KakaoMap,
  position: KakaoLatLng,
  role: Role,
  label: string,
  smooth: boolean,
): DeliveryPinOverlay {
  const root = document.createElement("div");
  root.className =
    "pointer-events-none absolute flex flex-col items-center gap-0.5 whitespace-nowrap";
  root.style.transform = "translate(-50%, -100%)";
  if (smooth) root.style.willChange = "transform";

  const labelElement = document.createElement("div");
  labelElement.className = cn(
    "rounded-pill px-1.5 py-0.5 text-2xs font-semibold text-white shadow-card",
    ROLE[role].bg,
  );
  labelElement.textContent = label;

  const pin = document.createElement("span");
  pin.setAttribute("aria-hidden", "true");
  pin.style.width = "30px";
  pin.style.height = "40px";
  pin.style.backgroundImage = `url("${pinImageSrc(ROLE[role].color)}")`;
  pin.style.backgroundPosition = "center";
  pin.style.backgroundRepeat = "no-repeat";
  pin.style.backgroundSize = "contain";

  root.append(labelElement, pin);

  class PinOverlay extends kakao.maps.AbstractOverlay {
    private position = position;
    private animation?: PinAnimation;

    private progress(animation: PinAnimation, now: number) {
      return Math.min(
        Math.max((now - animation.startedAt) / animation.durationMs, 0),
        1,
      );
    }

    private positionAt(animation: PinAnimation, progress: number) {
      return new kakao.maps.LatLng(
        animation.fromPosition.getLat() +
          (animation.toPosition.getLat() - animation.fromPosition.getLat()) *
            progress,
        animation.fromPosition.getLng() +
          (animation.toPosition.getLng() - animation.fromPosition.getLng()) *
            progress,
      );
    }

    private renderPoint(point: PixelPoint) {
      if (smooth) {
        // transform은 소수점 픽셀을 유지하고 브라우저 합성 레이어에서 처리돼 left/top보다 부드럽다.
        root.style.left = "0px";
        root.style.top = "0px";
        root.style.transform = `translate3d(${point.x}px, ${point.y}px, 0) translate(-50%, -100%)`;
        return;
      }
      // SMOOTH 모드가 꺼지면 기존 위치 반영 방식을 그대로 사용한다.
      root.style.left = `${point.x}px`;
      root.style.top = `${point.y}px`;
      root.style.transform = "translate(-50%, -100%)";
    }

    private cancelAnimation() {
      if (this.animation?.frame != null) {
        cancelAnimationFrame(this.animation.frame);
      }
      this.animation = undefined;
    }

    private animate = (now: number) => {
      const animation = this.animation;
      if (!animation) return;
      const progress = this.progress(animation, now);
      this.renderPoint(
        interpolatePoint(animation.fromPoint, animation.toPoint, progress),
      );

      if (progress < 1) {
        animation.frame = requestAnimationFrame(this.animate);
        return;
      }
      this.position = animation.toPosition;
      this.animation = undefined;
      this.renderPoint(animation.toPoint);
    };

    onAdd() {
      this.getPanels().overlayLayer.appendChild(root);
    }

    draw() {
      const projection = this.getProjection();
      const animation = this.animation;
      if (!animation) {
        this.renderPoint(projection.pointFromCoords(this.position));
        return;
      }

      // 지도 이동·확대 중에는 현재 진행 위치를 새 투영 좌표로 다시 잡고 남은 애니메이션을 이어간다.
      const now = performance.now();
      const progress = this.progress(animation, now);
      const currentPosition = this.positionAt(animation, progress);
      const remainingDurationMs = animation.durationMs * (1 - progress);
      const currentPoint = projection.pointFromCoords(currentPosition);
      const targetPoint = projection.pointFromCoords(animation.toPosition);

      animation.fromPosition = currentPosition;
      animation.fromPoint = currentPoint;
      animation.toPoint = targetPoint;
      animation.startedAt = now;
      animation.durationMs = Math.max(remainingDurationMs, 1);
      this.renderPoint(currentPoint);
    }

    onRemove() {
      this.cancelAnimation();
      root.remove();
    }

    setMap(nextMap: KakaoMap | null) {
      super.setMap(nextMap);
    }

    setPosition(nextPosition: KakaoLatLng) {
      this.cancelAnimation();
      this.position = nextPosition;
      if (this.getMap()) this.draw();
    }

    setSmoothPosition(nextPosition: KakaoLatLng, durationMs: number) {
      if (!smooth || !this.getMap()) {
        this.setPosition(nextPosition);
        return;
      }

      const now = performance.now();
      const previousAnimation = this.animation;
      const progress = previousAnimation
        ? this.progress(previousAnimation, now)
        : 1;
      const fromPosition = previousAnimation
        ? this.positionAt(previousAnimation, progress)
        : this.position;
      const projection = this.getProjection();
      const fromPoint = previousAnimation
        ? interpolatePoint(
            previousAnimation.fromPoint,
            previousAnimation.toPoint,
            progress,
          )
        : projection.pointFromCoords(fromPosition);

      this.cancelAnimation();
      this.position = nextPosition;
      this.animation = {
        fromPosition,
        toPosition: nextPosition,
        fromPoint,
        toPoint: projection.pointFromCoords(nextPosition),
        startedAt: now,
        durationMs: Math.max(durationMs, 1),
      };
      this.renderPoint(fromPoint);
      this.animation.frame = requestAnimationFrame(this.animate);
    }
  }

  const overlay = new PinOverlay();
  overlay.setMap(map);
  return overlay;
}

/** 핀 오버레이를 처음이면 생성하고, 이미 있으면 위치만 옮긴다. */
function upsertMarker(
  kakao: typeof window.kakao,
  map: KakaoMap,
  store: { overlay?: DeliveryPinOverlay },
  role: Role,
  coords: Coords,
  label: string,
  smooth = false,
) {
  const pos = new kakao.maps.LatLng(coords.latitude, coords.longitude);
  if (store.overlay) {
    store.overlay.setPosition(pos);
    return;
  }
  store.overlay = makePinOverlay(kakao, map, pos, role, label, smooth);
}

/** 드리미 핀을 처음이면 생성하고, 이미 있으면 픽셀 기반 애니메이션으로 옮긴다. */
function upsertSmoothDriver(
  kakao: typeof window.kakao,
  map: KakaoMap,
  store: { overlay?: DeliveryPinOverlay },
  coords: Coords,
  label: string,
  durationMs?: number,
) {
  const position = new kakao.maps.LatLng(coords.latitude, coords.longitude);
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
    "driver",
    label,
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
  route,
  height = 340,
  flat = false,
}: DeliveryRouteMapProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const kakaoRef = useRef<typeof window.kakao | null>(null);
  const mapRef = useRef<KakaoMap | null>(null);
  const storeRef = useRef<Record<Role, { overlay?: DeliveryPinOverlay }>>({
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
        const label = role === "driver" ? driverLabel : ROLE[role].label;
        upsertMarker(kakao, map, storeRef.current[role], role, coords, label);
      }
    });
  }, [status, pickup, dropoff, driver, driverLabel]);

  // SMOOTH 모드에서는 원본 좌표와 분리된 표시 좌표만 보간한다. 서버 전송·SSE 좌표는 건드리지 않는다.
  useEffect(() => {
    const motion = driverMotionRef.current;
    if (!DELIVERY_MAP_SMOOTH_MODE) return;
    if (!driver) return;
    const kakao = kakaoRef.current;
    const map = mapRef.current;
    if (status !== "ready" || !kakao || !map) return;

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
      plan.durationMs,
    );
  }, [status, driver, driverLabel]);

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

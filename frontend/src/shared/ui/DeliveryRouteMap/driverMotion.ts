import type { Coords } from "./DeliveryRouteMap";

/** 이 거리 이내의 좌표 변화는 정지 중 GPS 흔들림으로 본다. */
export const DRIVER_JITTER_RADIUS_M = 8;

const EARTH_RADIUS_M = 6_371_000;
const MIN_ANIMATION_DURATION_MS = 250;
const MAX_ANIMATION_DURATION_MS = 5_000;
const PREVIOUS_SPEED_WEIGHT = 0.6;

interface DriverMotionPlanOptions {
  previousTarget: Coords;
  previousReceivedAt: number;
  previousAverageSpeedMps?: number;
  nextTarget: Coords;
  receivedAt: number;
}

export interface DriverMotionPlan {
  averageSpeedMps: number;
  durationMs: number;
}

export interface PixelPoint {
  x: number;
  y: number;
}

function toRadians(degrees: number) {
  return (degrees * Math.PI) / 180;
}

/** 가까운 두 위·경도 사이의 지표면 거리를 미터로 계산한다. */
export function distanceMeters(from: Coords, to: Coords) {
  const latitudeDelta = toRadians(to.latitude - from.latitude);
  const longitudeDelta = toRadians(to.longitude - from.longitude);
  const fromLatitude = toRadians(from.latitude);
  const toLatitude = toRadians(to.latitude);

  const haversine =
    Math.sin(latitudeDelta / 2) ** 2 +
    Math.cos(fromLatitude) *
      Math.cos(toLatitude) *
      Math.sin(longitudeDelta / 2) ** 2;

  return (
    2 *
    EARTH_RADIUS_M *
    Math.atan2(Math.sqrt(haversine), Math.sqrt(1 - haversine))
  );
}

/** 두 픽셀 좌표 사이를 0~1 진행률로 선형 보간한다. */
export function interpolatePoint(
  from: PixelPoint,
  to: PixelPoint,
  progress: number,
) {
  const clampedProgress = Math.min(Math.max(progress, 0), 1);
  return {
    x: from.x + (to.x - from.x) * clampedProgress,
    y: from.y + (to.y - from.y) * clampedProgress,
  };
}

/** GPS 흔들림을 제외하고, 최근 평균 속도로 다음 좌표까지의 표시 이동 시간을 계산한다. */
export function planDriverMotion({
  previousTarget,
  previousReceivedAt,
  previousAverageSpeedMps,
  nextTarget,
  receivedAt,
}: DriverMotionPlanOptions): DriverMotionPlan | null {
  const distance = distanceMeters(previousTarget, nextTarget);
  if (distance <= DRIVER_JITTER_RADIUS_M) return null;

  const elapsedSeconds = Math.max(
    (receivedAt - previousReceivedAt) / 1_000,
    0.001,
  );
  const observedSpeedMps = distance / elapsedSeconds;
  const averageSpeedMps =
    previousAverageSpeedMps == null
      ? observedSpeedMps
      : previousAverageSpeedMps * PREVIOUS_SPEED_WEIGHT +
        observedSpeedMps * (1 - PREVIOUS_SPEED_WEIGHT);
  const durationMs = Math.min(
    Math.max((distance / averageSpeedMps) * 1_000, MIN_ANIMATION_DURATION_MS),
    MAX_ANIMATION_DURATION_MS,
  );

  return { averageSpeedMps, durationMs };
}

import { api } from "@/shared/api";

interface Coords {
  latitude: number;
  longitude: number;
}

type ProofIntent = "pickup" | "finish";

const GEOLOCATION_TIMEOUT_MS = 10_000;
const POSITION_MAX_AGE_MS = 15_000;

/** 두 좌표 사이의 하버사인 직선거리(m). */
export function distanceMeters(a: Coords, b: Coords): number {
  const EARTH_RADIUS_M = 6_371_000;
  const toRad = (deg: number) => (deg * Math.PI) / 180;
  const dLat = toRad(b.latitude - a.latitude);
  const dLon = toRad(b.longitude - a.longitude);
  const lat1 = toRad(a.latitude);
  const lat2 = toRad(b.latitude);
  const h =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) ** 2;
  return 2 * EARTH_RADIUS_M * Math.asin(Math.sqrt(h));
}

function getCurrentCoords(): Promise<Coords | null> {
  if (!("geolocation" in navigator)) return Promise.resolve(null);

  return new Promise((resolve) => {
    navigator.geolocation.getCurrentPosition(
      ({ coords }) =>
        resolve({ latitude: coords.latitude, longitude: coords.longitude }),
      () => resolve(null),
      {
        enableHighAccuracy: true,
        timeout: GEOLOCATION_TIMEOUT_MS,
        maximumAge: POSITION_MAX_AGE_MS,
      },
    );
  });
}

/**
 * 완료 시점의 최신 GPS와 픽업지/배달 도착지 사이 직선거리(m)를 계산한다.
 * 경고용 보조 기능이므로 위치나 상세 좌표를 얻지 못하면 null을 반환해 완료를 막지 않는다.
 */
export async function getCompletionDistance(
  orderId: string,
  intent: ProofIntent,
): Promise<number | null> {
  const [detail, current] = await Promise.all([
    api
      .getDeliveryDetail(orderId)
      .then(({ result }) => result ?? null)
      .catch(() => null),
    getCurrentCoords(),
  ]);

  if (!detail || !current) return null;

  const latitude =
    intent === "pickup"
      ? detail.originLatitude
      : detail.destinationLatitude;
  const longitude =
    intent === "pickup"
      ? detail.originLongitude
      : detail.destinationLongitude;
  if (latitude == null || longitude == null) return null;

  return distanceMeters(current, { latitude, longitude });
}

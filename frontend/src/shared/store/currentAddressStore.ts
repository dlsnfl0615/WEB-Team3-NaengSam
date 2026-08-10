import { create } from "zustand";
import { loadKakaoMaps } from "@/shared/lib/kakao";

/** GPS 위치 확인 제한 시간(ms). */
const GEOLOCATION_TIMEOUT_MS = 10_000;
/** 브라우저가 이 시간 이내에 구한 위치면 재사용한다(하드웨어 GPS 재조회 자체를 줄임). */
const POSITION_MAX_AGE_MS = 60_000;
/** 마지막 주소 조회로부터 이 시간 이내면 카카오 재호출 없이 캐시를 재사용한다. */
const ADDRESS_CACHE_TTL_MS = 5 * 60 * 1000;
/** 마지막 조회 좌표에서 이 거리(m) 이내로 움직였으면 주소가 안 바뀌었다고 보고 캐시를 재사용한다. */
const ADDRESS_CACHE_DISTANCE_M = 100;

interface Coords {
  latitude: number;
  longitude: number;
}

interface CurrentAddressStoreState {
  address: string | null;
  error: string | null;
  coords: Coords | null;
  fetchedAt: number | null;
  loading: boolean;
  /** 캐시가 5분·100m 기준으로 유효하면 그대로 재사용하고, 아니면 GPS→카카오 역지오코딩을 새로 수행한다. */
  ensureCurrentAddress: () => Promise<void>;
}

function geolocationMessage(error: GeolocationPositionError): string {
  switch (error.code) {
    case error.PERMISSION_DENIED:
      return "위치 권한이 필요해요. 권한을 허용한 뒤 다시 시도해주세요.";
    case error.TIMEOUT:
      return "위치 확인이 오래 걸려요. 실외로 이동한 뒤 다시 시도해주세요.";
    default:
      return "현재 위치를 확인할 수 없어요.";
  }
}

function getCurrentCoords(): Promise<Coords> {
  return new Promise((resolve, reject) => {
    if (!navigator.geolocation) {
      reject(new Error("이 브라우저에서는 위치를 사용할 수 없어요."));
      return;
    }
    navigator.geolocation.getCurrentPosition(
      ({ coords }) => resolve({ latitude: coords.latitude, longitude: coords.longitude }),
      (error) => reject(new Error(geolocationMessage(error))),
      {
        enableHighAccuracy: true,
        timeout: GEOLOCATION_TIMEOUT_MS,
        maximumAge: POSITION_MAX_AGE_MS,
      },
    );
  });
}

/** 두 좌표 사이의 하버사인 직선거리(m). */
function distanceMeters(a: Coords, b: Coords): number {
  const EARTH_RADIUS_M = 6_371_000;
  const toRad = (deg: number) => (deg * Math.PI) / 180;
  const dLat = toRad(b.latitude - a.latitude);
  const dLon = toRad(b.longitude - a.longitude);
  const lat1 = toRad(a.latitude);
  const lat2 = toRad(b.latitude);
  const h =
    Math.sin(dLat / 2) ** 2 + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) ** 2;
  return 2 * EARTH_RADIUS_M * Math.asin(Math.sqrt(h));
}

function reverseGeocode(coords: Coords): Promise<string> {
  return loadKakaoMaps().then(
    (kakao) =>
      new Promise<string>((resolve, reject) => {
        if (!kakao) {
          reject(new Error("지도 서비스를 사용할 수 없어요."));
          return;
        }
        const geocoder = new kakao.maps.services.Geocoder();
        geocoder.coord2Address(
          coords.longitude,
          coords.latitude,
          (
            result: { road_address?: { address_name: string }; address?: { address_name: string } }[],
            status: string,
          ) => {
            const found =
              status === kakao.maps.services.Status.OK
                ? result[0]?.road_address?.address_name ?? result[0]?.address?.address_name ?? null
                : null;
            if (!found) {
              reject(new Error("현재 위치의 주소를 찾을 수 없어요."));
              return;
            }
            resolve(found);
          },
        );
      }),
  );
}

/**
 * 현재 위치의 도로명주소 캐시. 화면을 오갈 때마다 다시 조회하면 카카오 역지오코딩 API가
 * 매번 호출되므로, 마지막 조회로부터 5분 이내이면서 100m 이내로 움직였으면 캐시를 그대로
 * 재사용하고 카카오를 다시 부르지 않는다.
 */
export const useCurrentAddressStore = create<CurrentAddressStoreState>((set, get) => ({
  address: null,
  error: navigator.geolocation ? null : "이 브라우저에서는 위치를 사용할 수 없어요.",
  coords: null,
  fetchedAt: null,
  loading: false,

  ensureCurrentAddress: async () => {
    if (get().loading) return;
    set({ loading: true });
    try {
      const coords = await getCurrentCoords();
      const { address, coords: cachedCoords, fetchedAt } = get();
      const isFresh = fetchedAt != null && Date.now() - fetchedAt < ADDRESS_CACHE_TTL_MS;
      const isNearby =
        cachedCoords != null && distanceMeters(cachedCoords, coords) < ADDRESS_CACHE_DISTANCE_M;

      if (address && isFresh && isNearby) {
        set({ loading: false, error: null });
        return;
      }

      const found = await reverseGeocode(coords);
      set({ address: found, coords, fetchedAt: Date.now(), error: null, loading: false });
    } catch (e) {
      set({
        error: e instanceof Error ? e.message : "현재 위치를 확인할 수 없어요.",
        loading: false,
      });
    }
  },
}));

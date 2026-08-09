import { useEffect, useState } from "react";
import { loadKakaoMaps } from "@/shared/lib/kakao";

/** GPS 위치 확인 제한 시간(ms). */
const GEOLOCATION_TIMEOUT_MS = 10_000;

export interface CurrentAddressState {
  /** 도로명주소(없으면 지번주소로 대체). 아직 못 구했으면 null. */
  address: string | null;
  /** geolocation 미지원/권한 거부/지오코딩 실패 등 오류 메시지(정상 시 null). */
  error: string | null;
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

/**
 * 브라우저 GPS로 현재 위치를 한 번 읽고, 카카오 맵 SDK(Geocoder.coord2Address)로
 * 도로명주소로 변환한다. road_address가 없는 지점(도로명 미부여 지역 등)은 지번주소로 대체한다.
 * VITE_KAKAO_MAP_KEY 미설정이면 지도와 동일하게 비활성 처리한다.
 */
export function useCurrentAddress(): CurrentAddressState {
  const [address, setAddress] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(() =>
    navigator.geolocation ? null : "이 브라우저에서는 위치를 사용할 수 없어요.",
  );

  useEffect(() => {
    if (!navigator.geolocation) return;
    let cancelled = false;

    navigator.geolocation.getCurrentPosition(
      ({ coords }) => {
        loadKakaoMaps().then((kakao) => {
          if (cancelled) return;
          if (!kakao) {
            setError("지도 서비스를 사용할 수 없어요.");
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
              if (cancelled) return;
              if (status !== kakao.maps.services.Status.OK || !result[0]) {
                setError("현재 위치의 주소를 찾을 수 없어요.");
                return;
              }
              const found =
                result[0].road_address?.address_name ??
                result[0].address?.address_name ??
                null;
              setAddress(found);
              if (!found) setError("현재 위치의 주소를 찾을 수 없어요.");
            },
          );
        });
      },
      (geoError) => {
        if (!cancelled) setError(geolocationMessage(geoError));
      },
      { enableHighAccuracy: true, timeout: GEOLOCATION_TIMEOUT_MS, maximumAge: 0 },
    );

    return () => {
      cancelled = true;
    };
  }, []);

  return { address, error };
}

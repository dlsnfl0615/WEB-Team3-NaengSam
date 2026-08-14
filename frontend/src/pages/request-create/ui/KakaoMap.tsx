import { useEffect, useRef, useState } from "react";
import { loadKakaoMaps } from "@/shared/lib";
import { makePinOverlay } from "@/shared/ui/DeliveryRouteMap/pinOverlay";
import type {
  PinOverlayHandle,
  PinStyle,
} from "@/shared/ui/DeliveryRouteMap/pinOverlay";

export interface KakaoMapProps {
  /** 출발지 도로명 주소(비면 마커 없음). */
  pickup?: string;
  /** 도착지 도로명 주소. */
  dropoff?: string;
}

/** 출발/도착 핀 색·라벨(DeliveryRouteMap과 같은 theme.css 토큰 hex·라벨 배경을 재사용). */
const PICKUP_STYLE: PinStyle = {
  color: "#0d1b3d", // navy-900
  label: "출발지",
  bg: "bg-navy-900",
};
const DROPOFF_STYLE: PinStyle = {
  color: "#b26a00", // status-warning
  label: "도착지",
  bg: "bg-status-warning",
};

/**
 * 픽업/도착지 도로명 주소를 지오코딩해 마커로 표시하는 미니 지도.
 * VITE_KAKAO_MAP_KEY가 없으면 자리표시 박스로 폴백한다.
 */
export function KakaoMap({ pickup, dropoff }: KakaoMapProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const [status, setStatus] = useState<"loading" | "ready" | "disabled">(
    "loading",
  );

  useEffect(() => {
    let cancelled = false;
    const overlays: PinOverlayHandle[] = [];
    loadKakaoMaps()
      .then((kakao) => {
        if (cancelled) return;
        if (!kakao || !containerRef.current) {
          setStatus("disabled");
          return;
        }
        setStatus("ready");

        const map = new kakao.maps.Map(containerRef.current, {
          center: new kakao.maps.LatLng(37.5665, 126.978), // 서울시청 기본값
          level: 5,
        });
        const geocoder = new kakao.maps.services.Geocoder();
        const bounds = new kakao.maps.LatLngBounds();
        let placed = 0;

        [
          { addr: pickup, style: PICKUP_STYLE },
          { addr: dropoff, style: DROPOFF_STYLE },
        ]
          .filter((p) => p.addr)
          .forEach(({ addr, style }) => {
            geocoder.addressSearch(
              addr as string,
              (result: { x: string; y: string }[], st: string) => {
                if (
                  cancelled ||
                  st !== kakao.maps.services.Status.OK ||
                  !result[0]
                )
                  return;
                const pos = new kakao.maps.LatLng(result[0].y, result[0].x);
                // 어느 핀이 출발/도착인지 색만으로는 알 수 없어 라벨 오버레이를 쓴다.
                overlays.push(makePinOverlay(kakao, map, pos, style));
                bounds.extend(pos);
                placed += 1;
                if (placed === 1) map.setCenter(pos);
                else map.setBounds(bounds);
              },
            );
          });
      })
      .catch(() => {
        if (!cancelled) setStatus("disabled");
      });

    return () => {
      cancelled = true;
      overlays.forEach((overlay) => overlay.setMap(null));
    };
  }, [pickup, dropoff]);

  if (status === "disabled") {
    return (
      <div className="flex h-[320px] items-center justify-center rounded-md border border-dashed border-line bg-canvas text-center text-2xs leading-[14px] text-muted">
        <p>
          지도 / MAP
          <br />
          주소를 선택하면 위치가 표시돼요
        </p>
      </div>
    );
  }

  return (
    <div className="relative h-[320px] w-full overflow-hidden rounded-md border border-line">
      <div ref={containerRef} className="h-full w-full" />
      {status === "loading" && (
        <div className="absolute inset-0 flex items-center justify-center bg-canvas text-2xs text-muted">
          지도 불러오는 중…
        </div>
      )}
    </div>
  );
}

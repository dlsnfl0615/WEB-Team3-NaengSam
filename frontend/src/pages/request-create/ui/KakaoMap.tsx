import { useEffect, useRef, useState } from "react";
import { loadKakaoMaps } from "./kakao";

export interface KakaoMapProps {
  /** 출발지 도로명 주소(비면 마커 없음). */
  pickup?: string;
  /** 도착지 도로명 주소. */
  dropoff?: string;
}

/** 출발/도착 마커 색(theme.css 토큰 hex 재사용). */
const PICKUP_COLOR = "#0d1b3d"; // navy-900
const DROPOFF_COLOR = "#00b7a7"; // teal-500

/** 지정한 색의 핀 모양 MarkerImage를 만든다(카카오 마커는 이미지 URI 필요). */
function pinImage(kakao: typeof window.kakao, color: string) {
  const svg =
    `<svg xmlns="http://www.w3.org/2000/svg" width="30" height="40" viewBox="0 0 30 40">` +
    `<path d="M15 0C6.716 0 0 6.716 0 15c0 10.5 15 25 15 25s15-14.5 15-25C30 6.716 23.284 0 15 0z" fill="${color}"/>` +
    `<circle cx="15" cy="15" r="5.5" fill="#fff"/></svg>`;
  return new kakao.maps.MarkerImage(
    `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`,
    new kakao.maps.Size(30, 40),
    { offset: new kakao.maps.Point(15, 40) },
  );
}

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
          { addr: pickup, color: PICKUP_COLOR },
          { addr: dropoff, color: DROPOFF_COLOR },
        ]
          .filter((p) => p.addr)
          .forEach(({ addr, color }) => {
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
                new kakao.maps.Marker({
                  map,
                  position: pos,
                  image: pinImage(kakao, color),
                });
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

import { cn } from "@/shared/lib/cn";
import { pinImageSrc } from "./pinImage";

type KakaoNamespace = typeof window.kakao;
export type KakaoMap = ReturnType<KakaoNamespace["maps"]["Map"]>;
export type KakaoLatLng = ReturnType<KakaoNamespace["maps"]["LatLng"]>;

export interface PinOverlayHandle {
  setMap(map: KakaoMap | null): void;
  setPosition(position: KakaoLatLng): void;
}

/** 핀 색·라벨 텍스트·라벨 배경(theme.css 토큰 유틸)을 함께 묶은 스타일. */
export interface PinStyle {
  color: string;
  label: string;
  bg: string;
}

/**
 * 핀과 역할 라벨을 하나의 AbstractOverlay로 만든다. DeliveryRouteMap(출발지·도착지·드리미)과
 * NearbyCallsMap(내 위치·주변 콜)이 공유한다. onClick을 주면 핀이 클릭 가능해진다(그 외엔
 * pointer-events를 꺼서 지도 조작을 방해하지 않는다).
 */
export function makePinOverlay(
  kakao: KakaoNamespace,
  map: KakaoMap,
  position: KakaoLatLng,
  style: PinStyle,
  onClick?: () => void,
): PinOverlayHandle {
  const root = document.createElement("div");
  root.className = cn(
    "absolute flex flex-col items-center gap-0.5 whitespace-nowrap",
    onClick ? "cursor-pointer" : "pointer-events-none",
  );
  root.style.transform = "translate(-50%, -100%)";
  if (onClick) root.addEventListener("click", onClick);

  const labelElement = document.createElement("div");
  labelElement.className = cn(
    "rounded-pill px-1.5 py-0.5 text-2xs font-semibold text-white shadow-card",
    style.bg,
  );
  labelElement.textContent = style.label;

  const pin = document.createElement("span");
  pin.setAttribute("aria-hidden", "true");
  pin.style.width = "30px";
  pin.style.height = "40px";
  pin.style.backgroundImage = `url("${pinImageSrc(style.color)}")`;
  pin.style.backgroundPosition = "center";
  pin.style.backgroundRepeat = "no-repeat";
  pin.style.backgroundSize = "contain";

  root.append(labelElement, pin);

  class PinOverlay extends kakao.maps.AbstractOverlay {
    private position = position;

    onAdd() {
      this.getPanels().overlayLayer.appendChild(root);
    }

    draw() {
      const point = this.getProjection().pointFromCoords(this.position);
      root.style.left = `${point.x}px`;
      root.style.top = `${point.y}px`;
    }

    onRemove() {
      root.remove();
    }

    setMap(nextMap: KakaoMap | null) {
      super.setMap(nextMap);
    }

    setPosition(nextPosition: KakaoLatLng) {
      this.position = nextPosition;
      if (this.getMap()) this.draw();
    }
  }

  const overlay = new PinOverlay();
  overlay.setMap(map);
  return overlay;
}

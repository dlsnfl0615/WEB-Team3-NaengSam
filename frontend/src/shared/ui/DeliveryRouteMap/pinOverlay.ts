import { cn } from "@/shared/lib/cn";
import { interpolatePoint, type PixelPoint } from "./driverMotion";
import { pinImageSrc } from "./pinImage";

type KakaoNamespace = typeof window.kakao;
export type KakaoMap = ReturnType<KakaoNamespace["maps"]["Map"]>;
export type KakaoLatLng = ReturnType<KakaoNamespace["maps"]["LatLng"]>;

export interface PinOverlayHandle {
  setMap(map: KakaoMap | null): void;
  setPosition(position: KakaoLatLng): void;
  setSmoothPosition(position: KakaoLatLng, durationMs: number): void;
  setImageSrc(imageSrc?: string): void;
  removeSmoothly(): void;
}

/** 핀 색·라벨 텍스트·라벨 배경(theme.css 토큰 유틸)을 함께 묶은 스타일. */
export interface PinStyle {
  color: string;
  label: string;
  bg: string;
  variant?: "pin" | "glow-dot";
  pinSize?: "default" | "small";
  imageSrc?: string;
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
  smooth = false,
): PinOverlayHandle {
  const isGlowDot = style.variant === "glow-dot";
  const anchorTransform = isGlowDot
    ? "translate(-50%, -50%)"
    : "translate(-50%, -100%)";
  const root = document.createElement("div");
  root.className = cn(
    "absolute flex flex-col items-center gap-0.5 whitespace-nowrap",
    onClick ? "cursor-pointer" : "pointer-events-none",
  );
  root.style.transform = anchorTransform;
  if (smooth) root.style.willChange = "transform";
  if (onClick) root.addEventListener("click", onClick);

  const content = document.createElement("div");
  content.className = cn(
    "ds-map-pin ds-map-pin-enter flex flex-col items-center gap-0.5",
    isGlowDot && "ds-map-glow-dot",
  );

  if (isGlowDot) {
    content.style.setProperty("--ds-map-dot-color", style.color);
  }

  const labelElement = document.createElement("div");
  labelElement.className = cn(
    "rounded-pill px-1.5 py-0.5 text-2xs font-semibold text-white shadow-card",
    style.bg,
  );
  labelElement.textContent = style.label;

  const pin = document.createElement("span");
  pin.setAttribute("aria-hidden", "true");
  pin.style.backgroundPosition = "center";
  pin.style.backgroundRepeat = "no-repeat";
  pin.style.backgroundSize = "contain";

  const setPinImage = (imageSrc?: string) => {
    pin.style.width = imageSrc
      ? "60px"
      : style.pinSize === "small"
        ? "24px"
        : "30px";
    pin.style.height = imageSrc
      ? "60px"
      : style.pinSize === "small"
        ? "32px"
        : "40px";
    pin.style.backgroundImage = `url("${imageSrc ?? pinImageSrc(style.color)}")`;
  };
  setPinImage(style.imageSrc);

  if (!isGlowDot) {
    content.append(labelElement, pin);
  }
  root.append(content);

  class PinOverlay extends kakao.maps.AbstractOverlay {
    private position = position;
    private animation?: PinAnimation;
    private enterFrame?: number;
    private removeTimer?: number;

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
        root.style.transform = `translate3d(${point.x}px, ${point.y}px, 0) ${anchorTransform}`;
        return;
      }
      // SMOOTH 모드가 꺼지면 기존 위치 반영 방식을 그대로 사용한다.
      root.style.left = `${point.x}px`;
      root.style.top = `${point.y}px`;
      root.style.transform = anchorTransform;
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
      this.enterFrame = requestAnimationFrame(() => {
        content.classList.remove("ds-map-pin-enter");
        this.enterFrame = undefined;
      });
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
      if (this.enterFrame != null) cancelAnimationFrame(this.enterFrame);
      if (this.removeTimer != null) window.clearTimeout(this.removeTimer);
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

    setImageSrc(imageSrc?: string) {
      setPinImage(imageSrc);
    }

    removeSmoothly() {
      if (!this.getMap() || content.classList.contains("ds-map-pin-exit")) return;
      if (window.matchMedia?.("(prefers-reduced-motion: reduce)").matches) {
        this.setMap(null);
        return;
      }
      content.classList.remove("ds-map-pin-enter");
      content.classList.add("ds-map-pin-exit");
      this.removeTimer = window.setTimeout(() => this.setMap(null), 220);
    }
  }

  const overlay = new PinOverlay();
  overlay.setMap(map);
  return overlay;
}

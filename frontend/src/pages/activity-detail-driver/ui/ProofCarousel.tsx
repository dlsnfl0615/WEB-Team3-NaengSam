import { useState } from "react";
import { Icon } from "@/shared/ui";

const PHOTOS = ["2024-07-21 14:20", "2024-07-21 14:22", "2024-07-21 14:28"];

/** 배송 완료 사진 캐러셀. 좌우 버튼으로 인증 사진을 넘깁니다(UI 전용). */
export function ProofCarousel() {
  const [index, setIndex] = useState(0);

  const move = (step: number) =>
    setIndex((prev) => (prev + step + PHOTOS.length) % PHOTOS.length);

  return (
    <div className="relative flex h-[210px] items-center justify-center overflow-hidden rounded-md bg-track">
      <span className="text-2xs text-muted">배송 완료 사진</span>

      <span className="absolute bottom-3 left-3 text-2xs text-muted">
        {PHOTOS[index]}
      </span>

      <button
        type="button"
        aria-label="이전 사진"
        onClick={() => move(-1)}
        className="absolute left-2 text-navy-900"
      >
        <Icon name="back" size={20} />
      </button>
      <button
        type="button"
        aria-label="다음 사진"
        onClick={() => move(1)}
        className="absolute right-2 rotate-180 text-navy-900"
      >
        <Icon name="back" size={20} />
      </button>
    </div>
  );
}

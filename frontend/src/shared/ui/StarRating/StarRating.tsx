import { Icon } from "../Icon/Icon";
import { cn } from "@/shared/lib/cn";

export interface StarRatingProps {
  /** 0이면 아직 선택 없음. */
  value: number;
  onChange: (value: number) => void;
}

const SCORES = [1, 2, 3, 4, 5];

/** 별 5개 평점 입력. 상호 배제 선택이므로 radiogroup으로 노출합니다. */
export function StarRating({ value, onChange }: StarRatingProps) {
  return (
    <div
      role="radiogroup"
      aria-label="평점"
      className="flex justify-center gap-2"
    >
      {SCORES.map((score) => (
        <button
          key={score}
          type="button"
          role="radio"
          aria-checked={score === value}
          aria-label={`${score}점`}
          onClick={() => onChange(score)}
          className={cn(score <= value ? "text-teal-700" : "text-track")}
        >
          <Icon name="star" size={22} />
        </button>
      ))}
    </div>
  );
}

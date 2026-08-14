import { Icon } from "../Icon/Icon";
import { cn } from "@/shared/lib/cn";

export interface StarRatingProps {
  /** 0이면 아직 선택 없음. */
  value: number;
  onChange?: (value: number) => void;
  /** 별점 등록 후처럼 더 이상 바꿀 수 없는 상태에서 읽기 전용으로 보여준다. */
  readOnly?: boolean;
}

const SCORES = [1, 2, 3, 4, 5];

/** 별 5개 평점 입력. 상호 배제 선택이므로 radiogroup으로 노출합니다. */
export function StarRating({ value, onChange, readOnly }: StarRatingProps) {
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
          disabled={readOnly}
          onClick={() => onChange?.(score)}
          className={cn(
            score <= value ? "text-status-warning" : "text-track",
            readOnly && "cursor-default",
          )}
        >
          <Icon name="star" size={22} />
        </button>
      ))}
    </div>
  );
}

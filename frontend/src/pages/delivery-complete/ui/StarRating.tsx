import { Icon } from "@/shared/ui";
import { cn } from "@/shared/lib/cn";

const STARS = [1, 2, 3, 4, 5];

export interface StarRatingProps {
  value: number;
  onChange?: (value: number) => void;
  /** 별점 등록 후처럼 더 이상 바꿀 수 없는 상태에서 읽기 전용으로 보여준다. */
  readOnly?: boolean;
}

/** 1~5점 별점 입력. */
export function StarRating({ value, onChange, readOnly }: StarRatingProps) {
  return (
    <div className="flex justify-center gap-2">
      {STARS.map((score) => (
        <button
          key={score}
          type="button"
          aria-label={`${score}점`}
          aria-pressed={score <= value}
          disabled={readOnly}
          onClick={() => onChange?.(score)}
          className={cn(
            score <= value ? "text-status-warning" : "text-line",
            readOnly && "cursor-default",
          )}
        >
          <Icon name="star" size={22} />
        </button>
      ))}
    </div>
  );
}

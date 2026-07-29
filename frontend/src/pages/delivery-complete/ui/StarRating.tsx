import { Icon } from "@/shared/ui";
import { cn } from "@/shared/lib/cn";

const STARS = [1, 2, 3, 4, 5];

export interface StarRatingProps {
  value: number;
  onChange: (value: number) => void;
}

/** 1~5점 별점 입력. */
export function StarRating({ value, onChange }: StarRatingProps) {
  return (
    <div className="flex justify-center gap-2">
      {STARS.map((score) => (
        <button
          key={score}
          type="button"
          aria-label={`${score}점`}
          aria-pressed={score <= value}
          onClick={() => onChange(score)}
          className={cn(score <= value ? "text-status-warning" : "text-line")}
        >
          <Icon name="star" size={22} />
        </button>
      ))}
    </div>
  );
}

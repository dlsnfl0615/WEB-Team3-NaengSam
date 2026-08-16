import { cn } from "@/shared/lib/cn";
import {
  ACTIVITY_FILTERS,
  ACTIVITY_FILTER_LABEL,
  type ActivityFilter,
} from "./records";

export interface FilterChipsProps {
  value: ActivityFilter;
  onChange: (value: ActivityFilter) => void;
}

/** 활동 내역 필터 칩. 상호 배제 선택이므로 radiogroup으로 노출합니다. */
export function FilterChips({ value, onChange }: FilterChipsProps) {
  return (
    <div role="radiogroup" aria-label="내역 필터" className="flex gap-2">
      {ACTIVITY_FILTERS.map((filter) => {
        const selected = filter === value;
        return (
          <button
            key={filter}
            type="button"
            role="radio"
            aria-checked={selected}
            onClick={() => onChange(filter)}
            className={cn(
              "rounded-pill px-3 py-1.5 text-xs font-semibold",
              selected ? "bg-teal-700 text-white" : "bg-track text-muted",
            )}
          >
            {ACTIVITY_FILTER_LABEL[filter]}
          </button>
        );
      })}
    </div>
  );
}

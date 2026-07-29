import { Card } from "../Card/Card";
import { cn } from "@/shared/lib/cn";

export interface RadioOptionProps {
  label: string;
  selected: boolean;
  onSelect: () => void;
}

/**
 * 상호 배제 단일 선택 카드(사유 선택 목록). 선택 시 테두리와 표시 원이 강조됩니다.
 */
export function RadioOption({ label, selected, onSelect }: RadioOptionProps) {
  return (
    <Card
      role="radio"
      aria-checked={selected}
      tabIndex={0}
      onClick={onSelect}
      className={cn(
        "flex cursor-pointer flex-col items-center gap-2 py-4",
        selected && "border-navy-900",
      )}
    >
      <span className="text-md font-semibold text-navy-900">{label}</span>
      <span
        className={cn(
          "size-5 rounded-pill border",
          selected ? "border-teal-500 bg-teal-50" : "border-line",
        )}
      />
    </Card>
  );
}

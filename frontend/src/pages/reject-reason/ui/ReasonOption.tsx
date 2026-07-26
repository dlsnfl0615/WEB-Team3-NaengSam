import { Card } from "@/shared/ui";
import { cn } from "@/shared/lib/cn";

export interface ReasonOptionProps {
  label: string;
  selected: boolean;
  onSelect: () => void;
}

/** 거절 사유 단일 선택 카드(라디오). */
export function ReasonOption({ label, selected, onSelect }: ReasonOptionProps) {
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

import { Card, Icon, type IconName } from "@/shared/ui";
import { cn } from "@/shared/lib/cn";

export interface PlaceItemProps {
  name: string;
  detail: string;
  /** 최근(time) / 즐겨찾기(star) 구분 아이콘 */
  icon: IconName;
  selected: boolean;
  onSelect: () => void;
}

/** 최근·추천 도착지 목록 아이템(단일 선택). */
export function PlaceItem({
  name,
  detail,
  icon,
  selected,
  onSelect,
}: PlaceItemProps) {
  return (
    <Card
      role="radio"
      aria-checked={selected}
      tabIndex={0}
      onClick={onSelect}
      className={cn(
        "flex cursor-pointer flex-col items-center gap-1 text-center",
        selected ? "border-navy-900" : "",
      )}
    >
      <Icon name={icon} size={16} className="text-teal-700" />
      <p className="text-base font-bold text-navy-900">{name}</p>
      <p className="text-2xs text-muted">{detail}</p>
    </Card>
  );
}

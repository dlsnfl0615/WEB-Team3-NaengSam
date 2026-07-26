import { Badge, Card, Icon, IconChip, toneForStatus } from "@/shared/ui";
import { cn } from "@/shared/lib/cn";
import type { ActivityRecord } from "./records";

export interface ActivityItemProps {
  record: ActivityRecord;
  /** 드리미 수익처럼 벌어들인 금액이면 티일로 강조합니다. */
  earned?: boolean;
  onClick?: () => void;
}

/** 활동 내역 리스트 아이템. 상단(아이콘칩·제목·상태) + 하단(시각·메모·금액). */
export function ActivityItem({ record, earned, onClick }: ActivityItemProps) {
  const tone =
    record.status === "완료" ? "neutral" : toneForStatus(record.status);

  return (
    <Card
      className="flex flex-col gap-3"
      role={onClick ? "button" : undefined}
      onClick={onClick}
      style={onClick ? { cursor: "pointer" } : undefined}
    >
      <div className="flex items-center gap-3">
        <IconChip name={record.icon} />
        <div className="min-w-0 flex-1">
          <p className="truncate text-base font-bold text-navy-900">
            {record.title}
          </p>
          <p className="truncate text-xs text-muted">{record.route}</p>
        </div>
        <Badge tone={tone}>{record.status}</Badge>
      </div>

      <div className="flex items-center gap-1 border-t border-track pt-3 text-xs text-muted">
        <span>{record.time}</span>
        <span>·</span>
        {record.rating !== undefined && (
          <>
            <Icon name="star" size={12} className="text-teal-700" />
            <span>{record.rating.toFixed(1)}</span>
          </>
        )}
        <span className="truncate">{record.note}</span>
        <span
          className={cn(
            "ml-auto text-sm font-bold",
            earned ? "text-teal-700" : "text-navy-900",
          )}
        >
          {record.amount}
        </span>
      </div>
    </Card>
  );
}

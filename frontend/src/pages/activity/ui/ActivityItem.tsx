import { Badge, Card, Icon, IconChip, toneForStatus } from "@/shared/ui";
import { cn } from "@/shared/lib/cn";
import type { ActivityRecord } from "./records";

export interface ActivityItemProps {
  record: ActivityRecord;
  /** 드리미 수익처럼 벌어들인 금액이면 티일로 강조합니다. */
  earned?: boolean;
  onClick?: () => void;
}

/** 아직 완료되지 않아 시각이 없는 건(진행 중)은 현재 시각으로 보여준다. */
function timeLabel(time: string): string {
  if (time) return time;
  const now = new Date();
  const hh = String(now.getHours()).padStart(2, "0");
  const mm = String(now.getMinutes()).padStart(2, "0");
  return `오늘 ${hh}:${mm}`;
}

/** 활동 내역 리스트 아이템. 상단(아이콘칩·제목·배달 ID·상태) + 하단(시각·별점·메모·금액). */
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
          {earned && (
            <p className="truncate text-2xs text-muted">
              배달 ID {record.id}
            </p>
          )}
        </div>
        <Badge tone={tone}>{record.status}</Badge>
      </div>

      <div className="flex items-center gap-1 border-t border-track pt-3 text-xs text-muted">
        <span>{timeLabel(record.time)}</span>
        <span>·</span>
        {earned &&
          (record.rating !== undefined ? (
            <>
              <Icon name="star" size={12} className="text-teal-700" />
              <span>{record.rating.toFixed(1)}</span>
            </>
          ) : (
            <span>리뷰 없음</span>
          ))}
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

import { cn } from "@/shared/lib/cn";

export interface DeliveryTimelineProps {
  /** 위에서부터 순서대로 표시할 단계 라벨. */
  steps: string[];
  /** 완료된 단계 수(앞에서부터 순서대로 채워짐). */
  completedCount: number;
  /** 완료된 단계별 시각(포맷된 문자열). steps와 같은 길이, 없거나 아직 완료 전이면 null. */
  timestamps?: (string | null)[];
  className?: string;
}

const ITEM_HEIGHT = 108;

/** 배달 진행 단계를 점 + 세로선으로 보여주는 타임라인(배민 배달 화면 스타일). */
export function DeliveryTimeline({
  steps,
  completedCount,
  timestamps,
  className,
}: DeliveryTimelineProps) {
  // 컨테이너가 auto 높이라 절대배치 자식의 height를 %로 주면 0으로 계산되므로
  // (containing block에 명시적 높이가 없으면 percentage height가 해석되지 않음), px로 직접 계산한다.
  const trackHeight = (steps.length - 1) * ITEM_HEIGHT;
  const progressFraction =
    steps.length > 1
      ? Math.max(0, Math.min(completedCount, steps.length) - 1) /
        (steps.length - 1)
      : 0;
  const fillHeight = trackHeight * progressFraction;

  return (
    <div className={cn("relative", className)}>
      <span
        className="absolute left-[4.5px] border-l border-dashed border-line"
        style={{ top: ITEM_HEIGHT / 2, bottom: ITEM_HEIGHT / 2 }}
      />
      <span
        className="absolute left-[4.5px] border-l border-dashed border-teal-700"
        style={{ top: ITEM_HEIGHT / 2, height: fillHeight }}
      />
      {steps.map((step, index) => {
        const done = index < completedCount;
        const timestamp = timestamps?.[index];
        return (
          <div
            key={step}
            className="relative flex items-center gap-3"
            style={{ minHeight: ITEM_HEIGHT }}
          >
            <span
              className={cn(
                "size-2.5 shrink-0 rounded-full",
                done ? "bg-teal-700" : "bg-track",
              )}
            />
            <span
              className={cn(
                "text-md font-bold",
                done ? "text-navy-900" : "text-muted",
              )}
            >
              {step}
            </span>
            {done && timestamp && (
              <>
                <span className="h-px min-w-3 flex-1 border-t border-dashed border-line" />
                <span className="shrink-0 text-xs text-muted">
                  {timestamp}
                </span>
              </>
            )}
          </div>
        );
      })}
    </div>
  );
}

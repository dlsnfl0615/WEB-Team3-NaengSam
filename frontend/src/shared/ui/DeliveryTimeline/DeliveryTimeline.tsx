import { cn } from "@/shared/lib/cn";

export interface DeliveryTimelineProps {
  /** 위에서부터 순서대로 표시할 단계 라벨. */
  steps: string[];
  /** 완료된 단계 수(앞에서부터 순서대로 채워짐). */
  completedCount: number;
  /** 완료된 단계별 시각(포맷된 문자열). steps와 같은 길이, 없거나 아직 완료 전이면 null. */
  timestamps?: (string | null)[];
  /**
   * 진행 중인 구간(마지막으로 완료된 단계와 다음 단계 사이) 안에서의 진행률(0~1).
   * 드리미의 남은 직선거리 기준. steps를 다 완료했거나 아직 시작 전 구간이면 무시된다. 기본 0.
   */
  segmentProgress?: number;
  className?: string;
}

const ITEM_HEIGHT = 108;
// 드리미 마스코트 핀 이미지(120x160, 3:4 비율) 크기. 세로선에 걸치는 폭이라 너무 크면 라벨과 겹친다.
const MARKER_WIDTH = 24;
const MARKER_HEIGHT = 32;

/** 배달 진행 단계를 점 + 세로선으로 보여주는 타임라인(배민 배달 화면 스타일). */
export function DeliveryTimeline({
  steps,
  completedCount,
  timestamps,
  segmentProgress = 0,
  className,
}: DeliveryTimelineProps) {
  // 컨테이너가 auto 높이라 절대배치 자식의 height를 %로 주면 0으로 계산되므로
  // (containing block에 명시적 높이가 없으면 percentage height가 해석되지 않음), px로 직접 계산한다.
  const trackHeight = (steps.length - 1) * ITEM_HEIGHT;
  const clampedCompleted = Math.max(0, Math.min(completedCount, steps.length));
  // 다음 단계로 넘어가는 중인 구간의 진행률까지 더해 이전엔 단계가 끝나야만 채워지던 선을
  // 드리미 위치가 갱신될 때마다 조금씩 채워지게 한다(구간을 다 지나면 다음 완료 처리로 자연히 넘어간다).
  const continuousProgress =
    clampedCompleted < steps.length
      ? clampedCompleted + Math.max(0, Math.min(segmentProgress, 1))
      : clampedCompleted;
  const progressFraction =
    steps.length > 1
      ? Math.max(0, Math.min(continuousProgress, steps.length) - 1) /
        (steps.length - 1)
      : 0;
  const fillHeight = trackHeight * progressFraction;
  // 진행 중인 구간이 있을 때만(전부 완료되지 않았을 때만) 걷는 위치 표시 점을 보여준다.
  const showProgressDot = clampedCompleted > 0 && clampedCompleted < steps.length;

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
      {showProgressDot && (
        <img
          src="/dreami-pin.png"
          alt=""
          aria-hidden="true"
          className="absolute"
          style={{
            left: 4.5 - MARKER_WIDTH / 2,
            top: ITEM_HEIGHT / 2 + fillHeight - MARKER_HEIGHT / 2,
            width: MARKER_WIDTH,
            height: MARKER_HEIGHT,
          }}
        />
      )}
    </div>
  );
}

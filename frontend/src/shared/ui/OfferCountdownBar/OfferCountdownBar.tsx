import { cn } from "@/shared/lib/cn";

export interface OfferCountdownBarProps {
  /** 남은 시간(초). 0이면 만료 문구로 바뀐다. */
  remainingSeconds: number;
  /** 0~100 진행률(남은 비율). */
  progressPercent: number;
  /** 좌측 라벨. 내가 응답하는 게 아니라 상대를 기다리는 화면이면 바꿔 준다. */
  label?: string;
  className?: string;
}

/**
 * 드리미(콜 수락)·부르미(드리미 확정) 팝업 공용 응답 카운트다운 바.
 * 5초 이하면 문구·바 색을 위험 색으로 바꾸고, 0초면 "응답 시간 만료"로 전환한다.
 */
export function OfferCountdownBar({
  remainingSeconds,
  progressPercent,
  label = "응답 가능 시간",
  className,
}: OfferCountdownBarProps) {
  const expired = remainingSeconds <= 0;
  const urgent = !expired && remainingSeconds <= 5;
  const clamped = Math.max(0, Math.min(100, progressPercent));

  return (
    <div className={cn("flex flex-col gap-1", className)}>
      <div className="flex items-center justify-between text-2xs">
        <p className="text-muted">{label}</p>
        <p
          className={cn(
            "font-bold",
            expired || urgent ? "text-status-danger" : "text-navy-900",
          )}
        >
          {expired ? "응답 시간 만료" : `${remainingSeconds}초 남음`}
        </p>
      </div>
      <div
        className="h-2 w-full overflow-hidden rounded-[5px] bg-track"
        role="progressbar"
        aria-valuenow={clamped}
        aria-valuemin={0}
        aria-valuemax={100}
      >
        <div
          className={cn(
            "h-full rounded-[5px] transition-[width]",
            urgent || expired ? "bg-status-danger" : "bg-teal-700",
          )}
          style={{ width: `${clamped}%` }}
        />
      </div>
    </div>
  );
}

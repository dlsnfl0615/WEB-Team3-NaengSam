import { Icon } from "@/shared/ui";
import { cn } from "@/shared/lib/cn";

const STEPS = ["위치", "물품", "사진·요청", "결제"] as const;

export interface RequestStepperProps {
  /** 현재 스텝(1~4) */
  current: number;
}

/** 부름 등록 4단계 진행 인디케이터(완료=체크, 현재=번호 강조, 이후=회색). */
export function RequestStepper({ current }: RequestStepperProps) {
  return (
    <div className="flex items-start justify-between px-1">
      {STEPS.map((label, i) => {
        const n = i + 1;
        const done = n < current;
        const active = n === current;
        return (
          <div key={label} className="flex flex-col items-center gap-1.5">
            <span
              className={cn(
                "flex size-7 items-center justify-center rounded-pill text-sm font-bold",
                done || active
                  ? "bg-navy-900 text-white"
                  : "bg-track text-muted",
              )}
            >
              {done ? <Icon name="check" size={12} /> : n}
            </span>
            <span className="text-xs text-muted">{label}</span>
          </div>
        );
      })}
    </div>
  );
}

import { cn } from '@/shared/lib/cn'

export interface ProgressBarProps {
  /** 0~100 진행률 */
  value: number
  className?: string
}

/** 배달 진행률 바. 트랙(track) + 채움(teal-700). */
export function ProgressBar({ value, className }: ProgressBarProps) {
  const clamped = Math.max(0, Math.min(100, value))
  return (
    <div
      className={cn('h-2 w-full overflow-hidden rounded-[5px] bg-track', className)}
      role="progressbar"
      aria-valuenow={clamped}
      aria-valuemin={0}
      aria-valuemax={100}
    >
      <div
        className="h-full rounded-[5px] bg-teal-700 transition-[width]"
        style={{ width: `${clamped}%` }}
      />
    </div>
  )
}

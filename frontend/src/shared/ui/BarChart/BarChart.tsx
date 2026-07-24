import { cn } from '@/shared/lib/cn'

export interface BarDatum {
  label: string
  value: number
}

export interface BarChartProps {
  data: BarDatum[]
  /** 마지막(또는 최대) 막대를 강조 */
  highlightLast?: boolean
  height?: number
  className?: string
}

/**
 * 절감 리포트용 간단한 막대 그래프. 값에 비례해 막대 높이를 계산합니다.
 */
export function BarChart({
  data,
  highlightLast = true,
  height = 140,
  className,
}: BarChartProps) {
  const max = Math.max(...data.map((d) => d.value), 1)
  return (
    <div
      className={cn('flex items-end justify-between gap-2', className)}
      style={{ height }}
    >
      {data.map((d, i) => {
        const isHighlight = highlightLast && i === data.length - 1
        return (
          <div
            key={d.label}
            className="flex flex-1 flex-col items-center justify-end gap-1.5"
            style={{ height: '100%' }}
          >
            <div
              className={cn(
                'w-full rounded-[6px]',
                isHighlight ? 'bg-teal-700' : 'bg-track',
              )}
              style={{ height: `${(d.value / max) * 100}%` }}
            />
            <span className="text-2xs text-muted">{d.label}</span>
          </div>
        )
      })}
    </div>
  )
}

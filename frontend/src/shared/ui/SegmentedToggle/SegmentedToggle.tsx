import { cn } from '@/shared/lib/cn'

export interface SegmentedToggleProps {
  options: [string, string]
  value: string
  onChange: (value: string) => void
  className?: string
}

/**
 * 2-세그먼트 토글(부르미/드리미). 선택된 세그먼트는 흰색 카드로 떠오릅니다.
 */
export function SegmentedToggle({
  options,
  value,
  onChange,
  className,
}: SegmentedToggleProps) {
  return (
    <div
      role="tablist"
      className={cn(
        'flex h-10 w-full items-center rounded-pill bg-line p-1',
        className,
      )}
    >
      {options.map((option) => {
        const selected = option === value
        return (
          <button
            key={option}
            role="tab"
            aria-selected={selected}
            type="button"
            onClick={() => onChange(option)}
            className={cn(
              'h-full flex-1 rounded-pill text-base font-bold transition',
              selected
                ? 'bg-surface text-navy-900 shadow-card'
                : 'text-muted',
            )}
          >
            {option}
          </button>
        )
      })}
    </div>
  )
}

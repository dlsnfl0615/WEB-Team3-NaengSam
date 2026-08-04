import { cn } from '@/shared/lib/cn'

export interface SegmentedToggleProps {
  options: [string, string]
  value: string
  onChange: (value: string) => void
  className?: string
  /** true면 전환을 막습니다(진행 중인 배달 등). */
  disabled?: boolean
}

/**
 * 2-세그먼트 토글(부르미/드리미). 선택된 세그먼트는 흰색 카드로 떠오릅니다.
 */
export function SegmentedToggle({
  options,
  value,
  onChange,
  className,
  disabled = false,
}: SegmentedToggleProps) {
  return (
    <div
      role="tablist"
      aria-disabled={disabled || undefined}
      className={cn(
        'flex h-10 w-full items-center rounded-pill bg-line p-1',
        disabled && 'opacity-60',
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
            disabled={disabled}
            onClick={() => onChange(option)}
            className={cn(
              'h-full flex-1 rounded-pill text-base font-bold transition',
              selected
                ? 'bg-surface text-navy-900 shadow-card'
                : 'text-muted',
              disabled && 'cursor-not-allowed',
            )}
          >
            {option}
          </button>
        )
      })}
    </div>
  )
}

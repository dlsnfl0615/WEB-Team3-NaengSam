import { Icon, type IconName } from '../Icon/Icon'
import { cn } from '@/shared/lib/cn'

export interface IconChipProps {
  name: IconName
  /** 칩 배경/아이콘 색 톤 */
  tone?: 'teal' | 'navy'
  size?: number
  className?: string
}

const TONES = {
  teal: 'bg-teal-50 text-teal-700',
  navy: 'bg-navy-900 text-white',
} as const

/**
 * 라운드 사각 아이콘 컨테이너(카드 리스트 아이템 앞에 붙는 아이콘칩).
 */
export function IconChip({
  name,
  tone = 'teal',
  size = 28,
  className,
}: IconChipProps) {
  return (
    <span
      className={cn(
        'inline-flex items-center justify-center rounded-sm',
        TONES[tone],
        className,
      )}
      style={{ width: size, height: size }}
    >
      <Icon name={name} size={Math.round(size * 0.57)} />
    </span>
  )
}

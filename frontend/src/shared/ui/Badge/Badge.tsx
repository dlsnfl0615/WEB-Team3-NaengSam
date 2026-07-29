import type { ReactNode } from 'react'
import { cn } from '@/shared/lib/cn'

export type BadgeTone = 'info' | 'warning' | 'danger' | 'neutral'

export interface BadgeProps {
  tone?: BadgeTone
  children: ReactNode
  className?: string
}

const TONES: Record<BadgeTone, string> = {
  info: 'bg-teal-50 text-teal-700',
  warning: 'bg-[#fff4e5] text-status-warning',
  danger: 'bg-[#fdecea] text-status-danger',
  neutral: 'bg-track text-muted',
}

/**
 * 상태 뱃지. 배송중/픽업중/매칭중/완료 → info, 지연 → warning, 사고/거절 → danger.
 */
export function Badge({ tone = 'info', children, className }: BadgeProps) {
  return (
    <span
      className={cn(
        'inline-flex items-center rounded-pill px-[11px] py-1 text-2xs font-semibold whitespace-nowrap',
        TONES[tone],
        className,
      )}
    >
      {children}
    </span>
  )
}

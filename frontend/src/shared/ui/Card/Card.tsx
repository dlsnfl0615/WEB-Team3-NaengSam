import type { HTMLAttributes, ReactNode } from 'react'
import { cn } from '@/shared/lib/cn'

export type CardVariant = 'surface' | 'hero' | 'accent'

export interface CardProps extends HTMLAttributes<HTMLDivElement> {
  variant?: CardVariant
  children: ReactNode
}

const VARIANTS: Record<CardVariant, string> = {
  surface: 'bg-surface border border-line shadow-card text-navy-900',
  hero: 'bg-navy-900 text-white',
  accent: 'bg-teal-50 border border-line shadow-card text-navy-900',
}

/**
 * 기본 카드 컨테이너. surface(흰색), hero(네이비), accent(연한 티일) variant.
 */
export function Card({
  variant = 'surface',
  className,
  children,
  ...rest
}: CardProps) {
  return (
    <div
      className={cn('rounded-md p-4', VARIANTS[variant], className)}
      {...rest}
    >
      {children}
    </div>
  )
}

import type { ButtonHTMLAttributes, ReactNode } from 'react'
import { cn } from '@/shared/lib/cn'

type ButtonVariant = 'primary' | 'navy' | 'outline'
type ButtonSize = 'md' | 'sm'

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant
  size?: ButtonSize
  /** 전체 너비로 확장 */
  block?: boolean
  /** 라벨 뒤 화살표(→) 표시 */
  arrow?: boolean
  children: ReactNode
}

const VARIANTS: Record<ButtonVariant, string> = {
  primary: 'bg-teal-500 text-white hover:brightness-95 active:brightness-90',
  navy: 'bg-navy-900 text-white hover:brightness-110 active:brightness-95',
  outline:
    'bg-surface text-navy-900 border border-line hover:bg-canvas active:bg-track',
}

const SIZES: Record<ButtonSize, string> = {
  md: 'h-11 px-5 text-md',
  sm: 'h-9 px-4 text-base',
}

export function Button({
  variant = 'primary',
  size = 'md',
  block = false,
  arrow = false,
  className,
  children,
  type = 'button',
  ...rest
}: ButtonProps) {
  return (
    <button
      type={type}
      className={cn(
        'inline-flex items-center justify-center gap-1 rounded-pill font-bold',
        'transition disabled:cursor-not-allowed disabled:opacity-40',
        'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-teal-500',
        VARIANTS[variant],
        SIZES[size],
        block ? 'w-full' : '',
        className,
      )}
      {...rest}
    >
      {children}
      {arrow && <span aria-hidden>→</span>}
    </button>
  )
}

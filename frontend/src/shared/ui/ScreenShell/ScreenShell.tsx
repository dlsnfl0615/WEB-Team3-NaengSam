import type { ReactNode } from 'react'
import { cn } from '@/shared/lib/cn'

export interface ScreenShellProps {
  children: ReactNode
  className?: string
}

/**
 * 모바일 화면 공통 셸. 폰 목업 없이 가운데 정렬된 390px 기준(max-w-[420px]) 폭으로
 * 렌더합니다. 모든 화면(screens/*)의 최상위 래퍼로 사용하세요.
 */
export function ScreenShell({ children, className }: ScreenShellProps) {
  return (
    <div className="flex min-h-svh justify-center bg-canvas">
      <div
        className={cn(
          'flex min-h-svh w-full max-w-[420px] flex-col px-4 pb-4 pt-6',
          className,
        )}
      >
        {children}
      </div>
    </div>
  )
}

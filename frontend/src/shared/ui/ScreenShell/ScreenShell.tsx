import type { ReactNode } from 'react'
import { cn } from '@/shared/lib/cn'

export interface ScreenShellProps {
  children: ReactNode
  /** 화면 바닥에 항상 고정되는 하단 슬롯(BottomNav 등). */
  footer?: ReactNode
  className?: string
}

/**
 * 모바일 화면 공통 셸. 폰 목업 없이 가운데 정렬된 390px 기준(max-w-[420px]) 폭으로
 * 렌더합니다. 모든 화면(screens/*)의 최상위 래퍼로 사용하세요.
 *
 * 뷰포트를 h-svh 로 고정하고 콘텐츠 영역만 스크롤하므로, footer 로 넘긴 요소는
 * 스크롤과 무관하게 항상 바닥에 붙습니다(PWA standalone 세이프에어리어까지 흡수).
 */
export function ScreenShell({ children, footer, className }: ScreenShellProps) {
  return (
    <div className="flex h-dvh justify-center bg-canvas">
      <div className="flex h-dvh w-full max-w-[420px] flex-col bg-canvas">
        <div
          className={cn(
            'flex min-h-0 flex-1 flex-col overflow-y-auto overscroll-contain px-4 pb-4 pt-6',
            className,
          )}
        >
          {children}
        </div>
        {footer && (
          <div className="shrink-0 px-4 pt-4 pb-[calc(env(safe-area-inset-bottom,0px)+1rem)]">
            {footer}
          </div>
        )}
      </div>
    </div>
  )
}

import type { ReactNode } from 'react'
import { Icon, type IconName } from '../Icon/Icon'

export interface ToastProps {
  icon?: IconName
  title: string
  description?: ReactNode
  /** 우측 액션(예: 확인 버튼) */
  action?: ReactNode
}

/**
 * 알림 토스트(드리미 요청 토스트 등). 네이비 배경의 떠 있는 카드.
 */
export function Toast({ icon = 'bell', title, description, action }: ToastProps) {
  return (
    <div className="flex items-center gap-3 rounded-md bg-navy-900 p-3.5 text-white shadow-elevated">
      <span className="flex h-9 w-9 items-center justify-center rounded-sm bg-navy-700">
        <Icon name={icon} size={20} className="text-white" />
      </span>
      <div className="min-w-0 flex-1">
        {/* 물품명처럼 긴 제목은 한 줄로 잘라내기보다 두 줄까지 접어 보여준다. */}
        <p className="line-clamp-2 break-words text-base font-bold">{title}</p>
        {description && (
          <div className="text-xs text-track">{description}</div>
        )}
      </div>
      {action}
    </div>
  )
}

import type { ReactNode } from 'react'

export interface SectionHeaderProps {
  title: string
  /** 제목 옆 카운트(예: 2) */
  count?: ReactNode
  /** 우측 링크 라벨(예: "전체 보기") */
  action?: string
  onAction?: () => void
}

/** 섹션 제목 + 카운트 + 우측 링크. */
export function SectionHeader({
  title,
  count,
  action,
  onAction,
}: SectionHeaderProps) {
  return (
    <div className="flex items-center justify-between">
      <div className="flex items-baseline gap-1.5">
        <h2 className="text-lg font-bold text-navy-900">{title}</h2>
        {count !== undefined && (
          <span className="text-base font-bold text-muted">{count}</span>
        )}
      </div>
      {action && (
        <button
          type="button"
          onClick={onAction}
          className="text-sm text-teal-700"
        >
          {action}
        </button>
      )}
    </div>
  )
}

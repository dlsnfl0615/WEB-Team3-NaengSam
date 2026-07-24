import { Icon } from '../Icon/Icon'

export interface LocationBarProps {
  location: string
  /** 우측 상태 텍스트(예: Connected) */
  status?: string
}

/** pin 아이콘 + 위치 + 우측 상태. 홈 화면 상단 위치 바. */
export function LocationBar({ location, status }: LocationBarProps) {
  return (
    <div className="flex h-[39px] items-center gap-2 rounded-md border border-line bg-surface px-3 shadow-card">
      <Icon name="pin" size={18} className="text-navy-900" />
      <span className="flex-1 truncate text-md text-navy-900">{location}</span>
      {status && <span className="text-xs text-muted">{status}</span>}
    </div>
  )
}

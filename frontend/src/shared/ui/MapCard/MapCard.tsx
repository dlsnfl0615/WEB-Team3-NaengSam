import type { ReactNode } from 'react'
import { Icon } from '../Icon/Icon'
import { cn } from '@/shared/lib/cn'

export interface MapCardProps {
  /** 지도 위에 겹쳐 표시할 정보(예: 거리·ETA 배지) */
  overlay?: ReactNode
  /** 지도 영역 높이(px) */
  height?: number
  className?: string
  /** 실제 지도 타일/이미지를 넣을 때 사용 */
  children?: ReactNode
}

/**
 * 지도 화면용 카드 래퍼. children 으로 실제 지도(타일/이미지)를 주입하고,
 * 없으면 플레이스홀더 배경 + 중앙 핀을 렌더합니다.
 */
export function MapCard({
  overlay,
  height = 220,
  className,
  children,
}: MapCardProps) {
  return (
    <div
      className={cn(
        'relative overflow-hidden rounded-md border border-line bg-track',
        className,
      )}
      style={{ height }}
    >
      {children ?? (
        <div className="flex h-full items-center justify-center bg-[linear-gradient(135deg,#eef1f5_0%,#e3e6ec_100%)]">
          <Icon name="pin" size={32} className="text-teal-700" />
        </div>
      )}
      {overlay && (
        <div className="absolute inset-x-3 bottom-3 flex justify-center">
          {overlay}
        </div>
      )}
    </div>
  )
}

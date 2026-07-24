import { Card } from '../Card/Card'
import { IconChip } from '../IconChip/IconChip'
import { Badge } from '../Badge/Badge'
import { toneForStatus } from '../Badge/toneForStatus'
import { ProgressBar } from '../ProgressBar/ProgressBar'
import type { IconName } from '../Icon/Icon'

export interface DeliveryCardProps {
  icon: IconName
  title: string
  route: string
  status: string
  /** 0~100 진행률. 생략 시 진행바를 숨깁니다. */
  progress?: number
  onClick?: () => void
}

/**
 * 진행 중인 배송 리스트 아이템.
 * 아이콘칩 + 제목/경로 + 상태 뱃지 + 진행바 조합.
 */
export function DeliveryCard({
  icon,
  title,
  route,
  status,
  progress,
  onClick,
}: DeliveryCardProps) {
  return (
    <Card
      className="flex flex-col gap-2"
      role={onClick ? 'button' : undefined}
      onClick={onClick}
      style={onClick ? { cursor: 'pointer' } : undefined}
    >
      <div className="flex items-center gap-3">
        <IconChip name={icon} />
        <div className="min-w-0 flex-1">
          <p className="truncate text-base font-bold text-navy-900">{title}</p>
          <p className="truncate text-xs text-muted">{route}</p>
        </div>
        <Badge tone={toneForStatus(status)}>{status}</Badge>
      </div>
      {progress !== undefined && <ProgressBar value={progress} />}
    </Card>
  )
}

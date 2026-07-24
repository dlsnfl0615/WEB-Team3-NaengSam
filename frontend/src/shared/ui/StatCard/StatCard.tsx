import { Card } from '../Card/Card'

export interface StatCardProps {
  label: string
  value: string
  /** accent = 연한 티일 배경 강조 */
  variant?: 'default' | 'accent'
}

/** 라벨 + 큰 숫자 값의 통계 카드(총 이용, 절감 금액 등). */
export function StatCard({ label, value, variant = 'default' }: StatCardProps) {
  return (
    <Card variant={variant === 'accent' ? 'accent' : 'surface'} className="p-3.5">
      <p className="text-2xs text-muted">{label}</p>
      <p className="mt-1.5 text-xl font-bold text-navy-900">{value}</p>
    </Card>
  )
}

import type { BadgeTone } from './Badge'

/** 배달 상태 문자열 → 뱃지 tone 매핑 헬퍼. */
export function toneForStatus(status: string): BadgeTone {
  if (status.includes('지연')) return 'warning'
  if (status.includes('사고') || status.includes('거절')) return 'danger'
  return 'info'
}

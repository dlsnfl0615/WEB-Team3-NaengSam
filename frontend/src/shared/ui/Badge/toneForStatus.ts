import type { BadgeTone } from './Badge'

/** 배달 상태 문자열 → 뱃지 tone 매핑 헬퍼. */
export function toneForStatus(status: string): BadgeTone {
  if (status.includes('지연')) return 'warning'
  if (status.includes('사고') || status.includes('거절')) return 'danger'
  // 취소는 종료 상태 — 브랜드 색(info)이면 활성·클릭 가능처럼 보여서 회색으로 둔다.
  if (status.includes('취소')) return 'neutral'
  return 'info'
}

import type { CSSProperties } from 'react'
import { ICONS, type IconName } from './icons'

export type { IconName }

export interface IconProps {
  name: IconName
  /** px 크기(정사각형). 기본 20. */
  size?: number
  className?: string
  'aria-label'?: string
}

/**
 * 디자인 시스템 아이콘. SVG를 인라인으로 렌더하고 stroke/fill 을 currentColor 로
 * 재색합니다(.ds-icon CSS). 색은 `color`(text-*)로 제어합니다.
 * 예: <Icon name="home" className="text-teal-700" /> → 티일색 홈 아이콘.
 */
export function Icon({
  name,
  size = 20,
  className = '',
  'aria-label': ariaLabel,
}: IconProps) {
  const style: CSSProperties = { width: size, height: size }
  return (
    <span
      className={`ds-icon ${className}`}
      style={style}
      role={ariaLabel ? 'img' : 'presentation'}
      aria-label={ariaLabel}
      aria-hidden={ariaLabel ? undefined : true}
      dangerouslySetInnerHTML={{ __html: ICONS[name] }}
    />
  )
}

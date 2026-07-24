import { Icon, type IconName } from '../Icon/Icon'
import { cn } from '@/shared/lib/cn'

export interface NavItem {
  name: IconName
  label: string
  key: string
}

export interface BottomNavProps {
  items?: NavItem[]
  active: string
  onChange?: (key: string) => void
}

const DEFAULT_ITEMS: NavItem[] = [
  { name: 'home', label: '홈', key: 'home' },
  { name: 'activity', label: '활동', key: 'activity' },
  { name: 'point', label: '수익', key: 'point' },
  { name: 'profile', label: '마이', key: 'profile' },
]

/** 하단 탭 바. 활성 탭은 teal-700, 비활성은 muted. */
export function BottomNav({
  items = DEFAULT_ITEMS,
  active,
  onChange,
}: BottomNavProps) {
  return (
    <nav className="flex items-start justify-between border-t border-track pt-2.5">
      {items.map((item) => {
        const isActive = item.key === active
        return (
          <button
            key={item.key}
            type="button"
            onClick={() => onChange?.(item.key)}
            className={cn(
              'flex flex-1 flex-col items-center gap-1',
              isActive ? 'text-teal-700' : 'text-muted',
            )}
            aria-current={isActive ? 'page' : undefined}
          >
            <Icon name={item.name} size={24} />
            <span
              className={cn(
                'text-xs',
                isActive ? 'font-bold' : 'font-normal',
              )}
            >
              {item.label}
            </span>
          </button>
        )
      })}
    </nav>
  )
}

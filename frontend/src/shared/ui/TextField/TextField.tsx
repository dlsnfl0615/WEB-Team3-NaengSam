import type { InputHTMLAttributes } from 'react'
import { Icon, type IconName } from '../Icon/Icon'
import { cn } from '@/shared/lib/cn'

export interface TextFieldProps
  extends InputHTMLAttributes<HTMLInputElement> {
  label?: string
  /** 왼쪽 아이콘(예: search) */
  leadingIcon?: IconName
}

/** 텍스트 입력 필드. leadingIcon 을 주면 검색 필드처럼 동작합니다. */
export function TextField({
  label,
  leadingIcon,
  className,
  id,
  ...rest
}: TextFieldProps) {
  return (
    <label className="flex flex-col gap-1.5">
      {label && (
        <span className="text-sm font-semibold text-navy-900">{label}</span>
      )}
      <span className="flex items-center gap-2 rounded-md border border-line bg-surface px-3.5 py-3 focus-within:border-teal-500">
        {leadingIcon && (
          <Icon name={leadingIcon} size={18} className="text-muted" />
        )}
        <input
          id={id}
          className={cn(
            'w-full bg-transparent text-md text-navy-900 outline-none placeholder:text-muted',
            className,
          )}
          {...rest}
        />
      </span>
    </label>
  )
}

/** search 아이콘이 붙은 검색 전용 필드. */
export function SearchField(props: Omit<TextFieldProps, 'leadingIcon'>) {
  return <TextField leadingIcon="search" {...props} />
}

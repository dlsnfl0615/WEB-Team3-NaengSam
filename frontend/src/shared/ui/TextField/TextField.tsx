import type { InputHTMLAttributes } from 'react'
import { Icon, type IconName } from '../Icon/Icon'
import { cn } from '@/shared/lib/cn'

export interface TextFieldProps
  extends InputHTMLAttributes<HTMLInputElement> {
  label?: string
  /** 왼쪽 아이콘(예: search) */
  leadingIcon?: IconName
  /** 검증 에러 메시지. 있으면 빨간 테두리와 함께 아래에 표시합니다. */
  error?: string
}

/** 텍스트 입력 필드. leadingIcon 을 주면 검색 필드처럼 동작합니다. */
export function TextField({
  label,
  leadingIcon,
  error,
  className,
  id,
  ...rest
}: TextFieldProps) {
  // 테두리·배경은 바깥 래퍼가 그리므로, 비활성 상태도 래퍼에서 표현해야 눈에 보인다.
  // (bg-canvas는 앱 배경색이라 화면과 구분이 안 돼 bg-track을 쓴다.)
  const disabled = rest.disabled === true

  return (
    <label className="flex flex-col gap-1.5">
      {label && (
        <span className="text-sm font-semibold text-navy-900">{label}</span>
      )}
      <span
        className={cn(
          'flex items-center gap-2 rounded-md border px-3.5 py-3',
          disabled
            ? 'cursor-not-allowed border-line bg-track'
            : error
              ? 'border-status-danger bg-surface'
              : 'border-line bg-surface focus-within:border-teal-500',
        )}
      >
        {leadingIcon && (
          <Icon name={leadingIcon} size={18} className="text-muted" />
        )}
        <input
          id={id}
          aria-invalid={error ? true : undefined}
          className={cn(
            'w-full bg-transparent text-md text-navy-900 outline-none placeholder:text-muted disabled:cursor-not-allowed disabled:text-muted',
            className,
          )}
          {...rest}
        />
      </span>
      {error && <span className="text-2xs text-status-danger">{error}</span>}
    </label>
  )
}

/** search 아이콘이 붙은 검색 전용 필드. */
export function SearchField(props: Omit<TextFieldProps, 'leadingIcon'>) {
  return <TextField leadingIcon="search" {...props} />
}

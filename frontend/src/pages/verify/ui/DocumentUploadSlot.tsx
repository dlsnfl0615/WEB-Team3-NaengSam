import { useRef, useState } from 'react'
import { Icon } from '@/shared/ui'

export interface DocumentUploadSlotProps {
  label: string
  onSelect: (file: File) => void
  disabled?: boolean
}

/**
 * 신분증/범죄이력조회서 선택 슬롯. 파일 선택 시 미리보기만 보여주고 File을 상위(VerifyScreen)로 올려준다.
 * 실제 presigned URL 발급/S3 PUT은 "본인인증" 버튼 클릭 시 한 번에 처리한다.
 */
export function DocumentUploadSlot({ label, onSelect, disabled }: DocumentUploadSlotProps) {
  const inputRef = useRef<HTMLInputElement>(null)
  const [preview, setPreview] = useState<string | null>(null)

  const onChangeFile = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    e.target.value = '' // 같은 파일 재선택 허용
    if (!file) return
    setPreview(URL.createObjectURL(file))
    onSelect(file)
  }

  return (
    <div className="flex flex-col gap-1.5">
      <input
        ref={inputRef}
        type="file"
        accept="image/*"
        className="hidden"
        onChange={onChangeFile}
      />
      <button
        type="button"
        onClick={() => inputRef.current?.click()}
        disabled={disabled}
        className="relative flex h-32 w-full flex-col items-center justify-center gap-1 overflow-hidden rounded-md border border-dashed border-line bg-canvas text-center text-muted disabled:opacity-60"
      >
        {preview ? (
          <img
            src={preview}
            alt={`${label} 미리보기`}
            className="h-full w-full object-cover"
          />
        ) : (
          <>
            <Icon name="camera" size={24} className="text-muted" />
            <span className="text-xs">사진 선택</span>
            <span className="text-xs">{label}</span>
          </>
        )}
      </button>
      {preview && <p className="text-2xs text-teal-700">{label} 선택됨</p>}
    </div>
  )
}

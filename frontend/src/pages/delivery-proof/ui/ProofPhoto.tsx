import { Icon, TextField } from "@/shared/ui";

export interface ProofPhotoProps {
  memo: string;
  onMemoChange: (value: string) => void;
}

/** 놓아둔 위치 사진 인증 영역(촬영 자리표시 + 썸네일 슬롯 + 전달 메모). */
export function ProofPhoto({ memo, onMemoChange }: ProofPhotoProps) {
  return (
    <div className="flex flex-col gap-3">
      <button
        type="button"
        className="flex h-[240px] flex-col items-center justify-center gap-1.5 rounded-md bg-navy-900 text-white"
      >
        <Icon name="camera" size={20} />
        <span className="text-2xs">탭하여 사진 촬영</span>
      </button>

      <div className="flex gap-2">
        <div className="flex size-[78px] items-center justify-center rounded-sm bg-teal-50 text-lg text-navy-900">
          +
        </div>
        <div className="flex size-[78px] items-center justify-center rounded-sm bg-track text-2xs text-muted">
          사진 1
        </div>
      </div>

      <TextField
        placeholder="전달 메모 (예: 우산꽂이 옆)"
        value={memo}
        onChange={(e) => onMemoChange(e.target.value)}
      />
    </div>
  );
}

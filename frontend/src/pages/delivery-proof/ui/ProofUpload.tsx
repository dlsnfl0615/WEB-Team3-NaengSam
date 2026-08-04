import { Icon } from "@/shared/ui";

export interface ProofUploadProps {
  /** 선택된 파일명(없으면 미선택 상태). */
  fileName: string | null;
  /** 파일 선택 콜백. */
  onFileSelected: (file: File) => void;
}

/** 실제 인증 사진 선택 영역(파일 입력 + 선택 파일명 표시). 픽업/전달 완료 인증에 공용. */
export function ProofUpload({ fileName, onFileSelected }: ProofUploadProps) {
  return (
    <div className="flex flex-col gap-3">
      <label className="flex h-[240px] cursor-pointer flex-col items-center justify-center gap-1.5 rounded-md bg-navy-900 text-white">
        <Icon name="camera" size={20} />
        <span className="text-2xs">탭하여 사진 선택</span>
        <input
          type="file"
          accept="image/*"
          className="hidden"
          onChange={(e) => {
            const f = e.target.files?.[0];
            if (f) onFileSelected(f);
          }}
        />
      </label>

      <div className="flex min-h-[44px] items-center rounded-sm bg-track px-3.5 text-2xs text-muted">
        {fileName ? (
          <span className="break-all text-navy-900">{fileName}</span>
        ) : (
          "선택된 파일 없음"
        )}
      </div>
    </div>
  );
}

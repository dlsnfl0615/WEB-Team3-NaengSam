import { Icon } from "@/shared/ui";

/** 수령인 서명 인증 영역(서명 패드 자리표시 + 서명자 정보). */
export function ProofSignature() {
  return (
    <div className="flex flex-col gap-2">
      <div className="flex h-[220px] flex-col items-center justify-center gap-1.5 rounded-md border border-dashed border-line bg-surface text-muted">
        <Icon name="check" size={20} />
        <span className="text-2xs">이 영역에 서명</span>
      </div>
      <div className="flex items-center justify-between">
        <span className="text-2xs text-muted">서명자: 민 (수령인)</span>
        <button type="button" className="text-2xs font-bold text-teal-700">
          다시 쓰기
        </button>
      </div>
    </div>
  );
}

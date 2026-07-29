import { Icon, TextField } from "@/shared/ui";
import { cn } from "@/shared/lib/cn";
import type { RequestForm, UpdateForm } from "./types";

const REQUEST_TAGS: RequestForm["requestTag"][] = [
  "없음",
  "도착 시 연락",
  "파손주의",
  "기타",
];

export interface StepPhotoProps {
  form: RequestForm;
  update: UpdateForm;
}

/** 스텝 3: 사진·요청 — 사진 업로드(자리표시) + 물건명/상세설명 + 요청사항 태그. */
export function StepPhoto({ form, update }: StepPhotoProps) {
  return (
    <div className="flex flex-col gap-4">
      <h2 className="text-lg font-bold tracking-[-0.4px] text-navy-900">
        물품 사진을 올려주세요
      </h2>

      {/* 사진 업로드 슬롯(자리표시) */}
      <div className="grid grid-cols-3 gap-2">
        <div className="flex h-[106px] flex-col items-center justify-center gap-1.5 rounded-sm border border-dashed border-line bg-canvas text-muted">
          <Icon name="camera" size={18} />
          <span className="text-2xs">사진 추가</span>
        </div>
        {["사진 1", "사진 2"].map((label) => (
          <div
            key={label}
            className="flex h-[106px] items-center justify-center rounded-sm border border-dashed border-line bg-canvas text-2xs text-muted"
          >
            {label}
          </div>
        ))}
      </div>

      {/* 물건명 */}
      <TextField
        label="물건명"
        placeholder="한줄로 설명해주세요"
        value={form.itemName}
        onChange={(e) => update({ itemName: e.target.value })}
      />

      {/* 상세 설명 */}
      <label className="flex flex-col gap-1.5">
        <span className="text-sm font-semibold text-navy-900">상세 설명</span>
        <textarea
          rows={3}
          placeholder="물품 상태·주의사항을 적어주세요 (예: 유리컵, 파손주의)"
          value={form.detail}
          onChange={(e) => update({ detail: e.target.value })}
          className="resize-none rounded-md border border-line bg-surface px-3.5 py-3 text-md text-navy-900 outline-none placeholder:text-muted focus-within:border-teal-500"
        />
      </label>

      {/* 배송 요청사항 */}
      <div className="flex flex-col gap-2">
        <p className="text-sm text-muted">배송 요청사항</p>
        <div role="radiogroup" className="flex flex-wrap gap-2">
          {REQUEST_TAGS.map((tag) => {
            const selected = form.requestTag === tag;
            return (
              <button
                key={tag}
                type="button"
                role="radio"
                aria-checked={selected}
                onClick={() => update({ requestTag: tag })}
                className={cn(
                  "rounded-pill px-3 py-1 text-sm font-semibold",
                  selected
                    ? "bg-teal-500 text-white"
                    : "bg-teal-50 text-teal-700",
                )}
              >
                {tag}
              </button>
            );
          })}
        </div>
        <TextField
          placeholder="추가 요청사항 직접 입력"
          value={form.etc}
          onChange={(e) => update({ etc: e.target.value })}
        />
      </div>
    </div>
  );
}

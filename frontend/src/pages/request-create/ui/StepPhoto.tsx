import { useRef, useState } from "react";
import { Icon, TextField } from "@/shared/ui";
import { cn } from "@/shared/lib/cn";
import { api, isApiError } from "@/shared/api";
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

/** 스텝 3: 사진·요청 — 사진 업로드(presigned) + 물건명/상세설명 + 요청사항 태그. */
export function StepPhoto({ form, update }: StepPhotoProps) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [preview, setPreview] = useState<string | null>(null);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const onSelectFile = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    e.target.value = ""; // 같은 파일 재선택 허용
    if (!file) return;
    setError(null);
    setUploading(true);
    try {
      const { result } = await api.getPresignedUrl({
        fileName: file.name,
        purpose: "ORDER_ITEM_IMAGE",
      });
      if (!result?.url || !result?.key)
        throw new Error("업로드 URL을 받지 못했어요.");
      // presigned URL로 S3에 직접 PUT(공통 axios 인스턴스 미사용).
      const res = await fetch(result.url, {
        method: "PUT",
        body: file,
        headers: { "Content-Type": file.type },
      });
      if (!res.ok) throw new Error("사진 업로드에 실패했어요.");
      update({ imageKey: result.key });
      setPreview(URL.createObjectURL(file));
    } catch (err) {
      setError(
        isApiError(err)
          ? err.message
          : err instanceof Error
            ? err.message
            : "사진 업로드에 실패했어요.",
      );
    } finally {
      setUploading(false);
    }
  };

  return (
    <div className="flex flex-col gap-4">
      <h2 className="text-lg font-bold tracking-[-0.4px] text-navy-900">
        물품 사진을 올려주세요
      </h2>

      {/* 사진 업로드 */}
      <input
        ref={inputRef}
        type="file"
        accept="image/*"
        className="hidden"
        onChange={onSelectFile}
      />
      <button
        type="button"
        onClick={() => inputRef.current?.click()}
        disabled={uploading}
        className="relative flex h-[160px] flex-col items-center justify-center gap-1.5 overflow-hidden rounded-sm border border-dashed border-line bg-canvas text-muted disabled:opacity-60"
      >
        {preview ? (
          <img
            src={preview}
            alt="선택한 물품 사진"
            className="h-full w-full object-cover"
          />
        ) : (
          <>
            <Icon name="camera" size={20} />
            <span className="text-2xs">
              {uploading ? "업로드 중…" : "사진 추가"}
            </span>
          </>
        )}
      </button>
      {form.imageKey && !uploading && (
        <p className="text-2xs text-teal-700">사진이 첨부됐어요.</p>
      )}
      {error && <p className="text-2xs text-status-danger">{error}</p>}

      {/* 물건명 */}
      <TextField
        label="물건명"
        placeholder="한줄로 설명해주세요"
        maxLength={50}
        value={form.itemName}
        onChange={(e) => update({ itemName: e.target.value })}
      />

      {/* 상세 설명 */}
      <label className="flex flex-col gap-1.5">
        <span className="text-sm font-semibold text-navy-900">상세 설명</span>
        <textarea
          rows={3}
          maxLength={255}
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
          maxLength={255}
          value={form.etc}
          onChange={(e) => update({ etc: e.target.value })}
        />
      </div>
    </div>
  );
}

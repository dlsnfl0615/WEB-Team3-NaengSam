import type { ReactNode } from "react";
import { cn } from "@/shared/lib/cn";

export interface BottomSheetProps {
  open: boolean;
  /** 스크린리더에 읽히는 시트 이름 */
  label: string;
  onClose: () => void;
  children: ReactNode;
  className?: string;
}

/**
 * 하단에서 올라오는 모달 시트. 뒤 배경은 어둡게·흐리게 처리하고,
 * 배경을 누르면 닫힙니다.
 */
export function BottomSheet({
  open,
  label,
  onClose,
  children,
  className,
}: BottomSheetProps) {
  if (!open) return null;

  return (
    <div className="fixed inset-0 z-50 flex justify-center">
      <button
        type="button"
        aria-label="닫기"
        onClick={onClose}
        className="absolute inset-0 bg-ink/40 backdrop-blur-sm"
      />

      <div
        role="dialog"
        aria-modal="true"
        aria-label={label}
        className={cn(
          "ds-sheet-up relative mt-auto flex max-h-[88svh] w-full max-w-[420px]",
          "flex-col gap-3 overflow-y-auto rounded-t-md bg-canvas px-4 pt-3 pb-4",
          className,
        )}
      >
        <span aria-hidden className="mx-auto h-1 w-10 rounded-pill bg-track" />
        {children}
      </div>
    </div>
  );
}

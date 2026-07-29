import type { ReactNode } from "react";
import { cn } from "@/shared/lib/cn";

export interface ModalProps {
  open: boolean;
  /** 스크린리더에 읽히는 모달 이름 */
  label: string;
  /** 있으면 배경을 눌러 닫을 수 있습니다. 없으면 배경은 눌러도 반응하지 않습니다. */
  onClose?: () => void;
  children: ReactNode;
  className?: string;
}

/**
 * 화면 가운데에 뜨는 모달. 뒤 배경은 회색조로 처리해 모달에 집중시킵니다.
 */
export function Modal({
  open,
  label,
  onClose,
  children,
  className,
}: ModalProps) {
  if (!open) return null;

  const backdrop = "absolute inset-0 bg-ink/40 backdrop-grayscale";

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center px-4">
      {onClose ? (
        <button
          type="button"
          aria-label="닫기"
          onClick={onClose}
          className={backdrop}
        />
      ) : (
        <div aria-hidden className={backdrop} />
      )}

      <div
        role="dialog"
        aria-modal="true"
        aria-label={label}
        className={cn("ds-modal-in relative w-full max-w-[420px]", className)}
      >
        {children}
      </div>
    </div>
  );
}

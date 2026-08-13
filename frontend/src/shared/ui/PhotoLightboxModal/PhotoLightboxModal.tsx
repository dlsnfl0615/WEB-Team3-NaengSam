import { Icon } from "../Icon/Icon";
import { Modal } from "../Modal/Modal";

export interface PhotoLightboxModalProps {
  open: boolean;
  /** 스크린리더에 읽히는 모달 이름(예: "픽업 사진"). */
  label: string;
  /** 없으면(아직 사진이 없으면) 자리표시 문구를 보여준다. */
  photoUrl?: string | null;
  /** 사진이 없을 때 보여줄 문구. */
  emptyMessage?: string;
  onClose: () => void;
}

/**
 * 사진 한 장을 크게 보여주는 라이트박스. `Modal` 위에 사진(또는 자리표시 문구)과
 * 오른쪽 위 X 닫기 버튼을 얹는다. 배경을 눌러도 닫힌다(Modal의 기본 동작).
 */
export function PhotoLightboxModal({
  open,
  label,
  photoUrl,
  emptyMessage = "아직 사진이 없어요.",
  onClose,
}: PhotoLightboxModalProps) {
  return (
    <Modal open={open} label={label} onClose={onClose}>
      <div className="relative overflow-hidden rounded-md bg-surface">
        <button
          type="button"
          aria-label="닫기"
          onClick={onClose}
          className="absolute right-3 top-3 z-10 flex size-8 items-center justify-center rounded-pill bg-ink/60 text-white"
        >
          <Icon name="close" size={18} />
        </button>

        {photoUrl ? (
          <img
            src={photoUrl}
            alt={label}
            className="max-h-[70vh] w-full object-contain"
          />
        ) : (
          <div className="flex h-[280px] flex-col items-center justify-center gap-1.5 text-muted">
            <Icon name="camera" size={22} />
            <span className="text-2xs">{emptyMessage}</span>
          </div>
        )}
      </div>
    </Modal>
  );
}

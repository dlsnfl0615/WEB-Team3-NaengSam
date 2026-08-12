import { useToastStore } from "@/shared/store/toastStore";
import { Icon } from "../Icon/Icon";
import { Toast } from "./Toast";

/** 전역 토스트를 화면 상단에 최대 3개까지 쌓아 표시한다. */
export function ToastViewport() {
  const toasts = useToastStore((state) => state.toasts);
  const dismiss = useToastStore((state) => state.dismiss);

  return (
    <div
      className="pointer-events-none fixed inset-x-0 top-4 z-50 mx-auto flex max-w-[420px] flex-col gap-2 px-4"
      aria-live="polite"
      aria-atomic="false"
    >
      {toasts.map((toast) => (
        <div key={toast.id} className="pointer-events-auto">
          <Toast
            icon={toast.icon}
            title={toast.title}
            description={toast.description}
            action={
              toast.persistent ? (
                <button
                  type="button"
                  aria-label="닫기"
                  className="shrink-0 text-white/70"
                  onClick={() => dismiss(toast.id)}
                >
                  <Icon name="close" size={16} />
                </button>
              ) : undefined
            }
          />
        </div>
      ))}
    </div>
  );
}

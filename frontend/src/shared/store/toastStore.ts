import type { ReactNode } from "react";
import { create } from "zustand";
import type { IconName } from "@/shared/ui";

/** 자동 소멸 기본 시간(ms). 기존 화면의 일회성 토스트 노출 시간과 동일하다. */
export const TOAST_DURATION_MS = 4_000;

/** 동시에 표시할 수 있는 토스트 상한. */
const MAX_TOASTS = 3;

export interface ToastItem {
  id: string;
  icon?: IconName;
  title: string;
  description?: ReactNode;
  /** true면 자동 소멸하지 않고 닫기 버튼으로만 사라진다. */
  persistent?: boolean;
  durationMs?: number;
  /** 같은 key의 토스트는 새로 쌓지 않고 기존 위치에서 교체한다. */
  dedupeKey?: string;
}

interface ToastState {
  toasts: ToastItem[];
  /** 토스트를 표시하고 생성되거나 재사용된 id를 반환한다. */
  show: (toast: Omit<ToastItem, "id">) => string;
  /** id에 해당하는 토스트와 자동 소멸 타이머를 제거한다. */
  dismiss: (id: string) => void;
  /** 현재 토스트와 모든 자동 소멸 타이머를 제거한다. */
  clear: () => void;
}

/** 렌더와 무관한 자동 소멸 타이머는 store 상태 밖에서 관리한다. */
const timers = new Map<string, ReturnType<typeof setTimeout>>();

function clearTimer(id: string) {
  const timer = timers.get(id);
  if (timer !== undefined) clearTimeout(timer);
  timers.delete(id);
}

export const useToastStore = create<ToastState>((set, get) => ({
  toasts: [],

  show: (toast) => {
    const current = get().toasts;
    const duplicate = toast.dedupeKey
      ? current.find((item) => item.dedupeKey === toast.dedupeKey)
      : undefined;
    const id = duplicate?.id ?? crypto.randomUUID();
    const item: ToastItem = { ...toast, id };

    clearTimer(id);

    const appended = duplicate
      ? current.map((existing) => (existing.id === id ? item : existing))
      : [...current, item];
    const overflow = appended.slice(0, Math.max(0, appended.length - MAX_TOASTS));
    overflow.forEach((removed) => clearTimer(removed.id));
    set({ toasts: appended.slice(-MAX_TOASTS) });

    if (!toast.persistent) {
      const timer = setTimeout(
        () => get().dismiss(id),
        toast.durationMs ?? TOAST_DURATION_MS,
      );
      timers.set(id, timer);
    }

    return id;
  },

  dismiss: (id) => {
    clearTimer(id);
    set((state) => ({
      toasts: state.toasts.filter((toast) => toast.id !== id),
    }));
  },

  clear: () => {
    timers.forEach((timer) => clearTimeout(timer));
    timers.clear();
    set({ toasts: [] });
  },
}));

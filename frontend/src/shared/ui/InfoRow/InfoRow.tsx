import type { ReactNode } from "react";

export interface InfoRowProps {
  label: string;
  children: ReactNode;
}

/** 배달 상세 정보 카드의 한 줄(라벨 - 값). */
export function InfoRow({ label, children }: InfoRowProps) {
  return (
    <div className="flex items-center justify-between text-sm">
      <span className="text-muted">{label}</span>
      <span className="flex items-center gap-1 font-bold text-navy-900">
        {children}
      </span>
    </div>
  );
}

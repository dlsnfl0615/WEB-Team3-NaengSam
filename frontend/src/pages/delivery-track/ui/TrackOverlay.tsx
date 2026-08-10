export interface TrackOverlayProps {
  /** "오전/오후 h시 m분" 형식의 배송 완료 예상 시각. 아직 계산 전이면 null. */
  arrivalTime: string | null;
}

/** 지도 위에 겹쳐 표시하는 배송 완료 예상 시각 배지. */
export function TrackOverlay({ arrivalTime }: TrackOverlayProps) {
  return (
    <div className="flex items-center gap-6 rounded-pill bg-navy-900 px-6 py-2.5 text-white">
      <div className="flex flex-col items-center">
        <span className="text-2xs opacity-70">배송 완료 예상</span>
        <span className="text-md font-bold">{arrivalTime ?? "계산 중…"}</span>
      </div>
    </div>
  );
}

export interface TrackOverlayProps {
  eta: string;
  distance: string;
}

/** 지도 위에 겹쳐 표시하는 예상 도착·남은 거리 배지. */
export function TrackOverlay({ eta, distance }: TrackOverlayProps) {
  return (
    <div className="flex items-center gap-6 rounded-pill bg-navy-900 px-6 py-2.5 text-white">
      <div className="flex flex-col items-center">
        <span className="text-2xs opacity-70">예상 도착</span>
        <span className="text-md font-bold">{eta}</span>
      </div>
      <div className="flex flex-col items-center">
        <span className="text-2xs opacity-70">남은 거리</span>
        <span className="text-md font-bold">{distance}</span>
      </div>
    </div>
  );
}

import { Card } from "../Card/Card";
import { Icon } from "../Icon/Icon";

export interface RouteCardProps {
  origin: string;
  destination: string;
}

/** 출발지 → 도착지를 점선으로 잇는 경로 카드. */
export function RouteCard({ origin, destination }: RouteCardProps) {
  return (
    <Card className="flex flex-col gap-1">
      <div className="flex items-center gap-3">
        <span className="size-7 rounded-pill bg-teal-50" />
        <div className="flex flex-col">
          <span className="text-2xs text-muted">출발지</span>
          <span className="text-md font-bold text-navy-900">{origin}</span>
        </div>
      </div>

      <span className="ml-3.5 h-4 border-l border-dashed border-line" />

      <div className="flex items-center gap-3">
        <span className="flex size-7 items-center justify-center rounded-pill bg-teal-50 text-teal-700">
          <Icon name="pin" size={14} />
        </span>
        <div className="flex flex-col">
          <span className="text-2xs text-muted">도착지</span>
          <span className="text-md font-bold text-navy-900">{destination}</span>
        </div>
      </div>
    </Card>
  );
}

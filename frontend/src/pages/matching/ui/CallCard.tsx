import { Badge, Button } from "@/shared/ui";
import { cn } from "@/shared/lib/cn";

export interface CallCardProps {
  /** 콜 번호(예: "#B-882") */
  code: string;
  price: string;
  /** 건물·장소 이름 */
  place: string;
  /** 층 이동 경로(예: "24F → 12F") */
  route: string;
  pickupDistance: string;
  /** 목적지 거리. 값이 없으면 항목을 숨긴다. */
  dropoffDistance?: string;
  /** 물품 유형. 값이 없으면 항목을 숨긴다. */
  itemType?: string;
  onReject: () => void;
  onAccept: () => void;
}

/**
 * 드리미에게 도착한 부름(콜) 카드(Figma node 191:1195).
 * 금액·경로·거리·물품 유형을 보여주고 콜을 수락하거나 거절합니다.
 */
export function CallCard({
  code,
  price,
  place,
  route,
  pickupDistance,
  dropoffDistance,
  itemType,
  onReject,
  onAccept,
}: CallCardProps) {
  return (
    <div className="flex flex-col gap-3 rounded-md border-2 border-status-success bg-surface p-4 shadow-card">
      <div className="flex items-start justify-between">
        <Badge tone="info">새로운 콜! {code}</Badge>
        <p className="text-xl font-bold text-teal-700">{price}</p>
      </div>

      <div className="text-xl font-bold tracking-[-0.4px] text-navy-900">
        <p>{place}</p>
        <p>{route}</p>
      </div>

      <div className="h-px bg-track" />

      <div className="flex items-start">
        <CallStat label="픽업 거리" value={pickupDistance} />
        {dropoffDistance && (
          <CallStat
            label="목적지 거리"
            value={dropoffDistance}
            className="ml-6"
          />
        )}
        {itemType && (
          <CallStat
            label="물품 유형"
            value={itemType}
            className="ml-auto text-right"
          />
        )}
      </div>

      <div className="flex gap-2">
        <Button
          variant="outline"
          className="border-transparent bg-line"
          onClick={onReject}
        >
          거절
        </Button>
        <Button variant="navy" className="flex-1" onClick={onAccept}>
          콜 수락
        </Button>
      </div>
    </div>
  );
}

interface CallStatProps {
  label: string;
  value: string;
  className?: string;
}

function CallStat({ label, value, className }: CallStatProps) {
  return (
    <div className={cn("flex flex-col", className)}>
      <p className="text-xs text-muted">{label}</p>
      <p className="text-md font-bold text-navy-900">{value}</p>
    </div>
  );
}

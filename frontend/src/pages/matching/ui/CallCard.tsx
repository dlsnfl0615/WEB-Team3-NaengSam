import {
  Badge,
  Button,
  DeliveryRouteMap,
  OfferCountdownBar,
  type Coords,
} from "@/shared/ui";
import { cn } from "@/shared/lib/cn";

export interface CallCardProps {
  /** 콜 번호(예: "#B-882") */
  code: string;
  price: string;
  /** 건물·장소 이름 */
  place: string;
  /** 출발지 → 도착지 경로 */
  route: string;
  pickup?: Coords; // 출발지(픽업 위치) 위도 경도
  dropoff?: Coords; // 도착지 위도 경도
  currentLocation?: Coords; // 드리미 현재 위치
  deliveryDistance: string;
  /** 픽업 후 배송에 걸리는 예상 시간 */
  eta: string;
  /** 목적지 거리. 값이 없으면 항목을 숨긴다. */
  dropoffDistance?: string;
  /** 물품 유형. 값이 없으면 항목을 숨긴다. */
  itemType?: string;
  /** 콜 수락 응답 카운트다운(드리미가 콜을 선택하는 시간). */
  countdown: { remainingSeconds: number; progressPercent: number };
  onReject: () => void;
  onAccept: () => void;
}

/**
 * 드리미에게 도착한 부름(콜) 카드(Figma node 191:1195).
 * 금액·경로·거리·물품 유형을 보여주고 콜을 수락하거나 거절합니다.
 */
export function CallCard({
  price,
  place,
  route,
  pickup,
  dropoff,
  currentLocation,
  deliveryDistance,
  eta,
  dropoffDistance,
  itemType,
  countdown,
  onReject,
  onAccept,
}: CallCardProps) {
  return (
    <div className="flex flex-col gap-3 rounded-md border-2 border-status-success bg-surface p-4 shadow-card">
      {/* 물품명은 길이를 통제할 수 없다. 카드 제목에서만큼은 자르지 않고 줄바꿈으로 전부 보여준다. */}
      <p className="break-words text-xl font-bold tracking-[-0.4px] text-navy-900">
        {place}
      </p>

      <DeliveryRouteMap
        pickup={pickup}
        dropoff={dropoff}
        driver={currentLocation}
        driverLabel="내 위치"
        pickupLabel={place}
        height={280}
      />

      <div className="flex items-start justify-between">
        <Badge tone="info">새로운 콜!</Badge>
        <p className="text-xl font-bold text-teal-700">{price}</p>
      </div>

      <p className="break-words text-base font-bold text-navy-900">{route}</p>

      <div className="h-px bg-track" />

      <div className="flex items-start">
        <CallStat label="배송 거리" value={deliveryDistance} />
        <CallStat
          label="픽업 후 배송"
          value={eta}
          className="ml-auto text-right"
        />
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

      <OfferCountdownBar
        remainingSeconds={countdown.remainingSeconds}
        progressPercent={countdown.progressPercent}
      />

      <div className="flex gap-2">
        <Button
          variant="outline"
          className="shrink-0 border-transparent bg-line"
          onClick={onReject}
        >
          거절
        </Button>
        <Button
          variant="navy"
          className="flex-1"
          onClick={onAccept}
          disabled={countdown.remainingSeconds <= 0}
        >
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

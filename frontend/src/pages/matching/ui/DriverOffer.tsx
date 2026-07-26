import { Button, Icon, IconChip } from "@/shared/ui";

export interface DriverOfferProps {
  name: string;
  rating: number;
  deliveries: number;
  /** 픽업 지점까지 남은 거리 */
  distance: string;
  onReject: () => void;
  onAccept: () => void;
}

/**
 * 매칭 중 도착한 드리미 요청 카드(수락·거절).
 */
export function DriverOffer({
  name,
  rating,
  deliveries,
  distance,
  onReject,
  onAccept,
}: DriverOfferProps) {
  return (
    <div className="flex flex-col gap-3 rounded-lg border border-teal-500 bg-white p-4 shadow-card">
      <p className="text-2xs font-bold text-teal-700">새 드리미 요청 도착!</p>

      <div className="flex items-start justify-between">
        <div className="flex items-center gap-2">
          <IconChip name="profile" size={36} />
          <div className="flex flex-col">
            <p className="text-base font-bold text-navy-900">{name}</p>
            <p className="flex items-center gap-1 text-2xs text-muted">
              <Icon name="star" size={12} />
              {rating} · 배송 {deliveries}건
            </p>
          </div>
        </div>

        <div className="flex flex-col items-end">
          <p className="text-2xs text-muted">픽업까지</p>
          <p className="text-base font-bold text-navy-900">{distance}</p>
        </div>
      </div>

      <div className="flex gap-2">
        <Button variant="outline" block onClick={onReject}>
          거절
        </Button>
        <Button variant="navy" block onClick={onAccept}>
          수락하기
        </Button>
      </div>
    </div>
  );
}

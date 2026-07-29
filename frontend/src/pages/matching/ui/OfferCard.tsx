import { Button, Icon, IconChip } from "@/shared/ui";

export interface OfferCardProps {
  /** 카드 상단 안내 문구(예: "새 드리미 요청 도착!") */
  heading: string;
  name: string;
  rating: number;
  /** 건수 라벨. 부르미가 볼 땐 "배송", 드리미가 볼 땐 "요청" */
  countLabel: string;
  count: number;
  /** 픽업 지점까지 남은 거리 */
  distance: string;
  onReject: () => void;
  onAccept: () => void;
}

/**
 * 부르미에게 도착한 드리미 요청 카드(수락·거절).
 * 드리미가 받는 부름은 필드가 달라 CallCard를 씁니다.
 */
export function OfferCard({
  heading,
  name,
  rating,
  countLabel,
  count,
  distance,
  onReject,
  onAccept,
}: OfferCardProps) {
  return (
    <div className="flex flex-col gap-3 rounded-lg border border-teal-500 bg-white p-4 shadow-card">
      <p className="text-2xs font-bold text-teal-700">{heading}</p>

      <div className="flex items-start justify-between">
        <div className="flex items-center gap-2">
          <IconChip name="profile" size={36} />
          <div className="flex flex-col">
            <p className="text-base font-bold text-navy-900">{name}</p>
            <p className="flex items-center gap-1 text-2xs text-muted">
              <Icon name="star" size={12} />
              {rating} · {countLabel} {count}건
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

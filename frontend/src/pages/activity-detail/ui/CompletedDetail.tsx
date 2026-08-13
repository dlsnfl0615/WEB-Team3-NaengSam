import { useState } from "react";
import {
  Badge,
  Button,
  Card,
  Icon,
  IconChip,
  InfoRow,
  RouteCard,
  StarRating,
} from "@/shared/ui";
import type { Delivery } from "@/shared/mock/types";

export interface CompletedDetailProps {
  delivery: Delivery | null;
}

/** 완료된 배달의 상세 내용(요약·경로·결제 정보·평가). */
export function CompletedDetail({ delivery }: CompletedDetailProps) {
  const [rating, setRating] = useState(0);

  const sizeLabel = delivery?.itemSize === "M" ? "중형(M)" : "소형(S)";
  const itemTypeLabel = delivery
    ? `${delivery.itemType} · ${sizeLabel}`
    : "서류 · 소형(S)";
  const counterpart = delivery?.driverName ?? delivery?.senderName ?? "민";
  const counterpartRating = delivery?.rating ?? 4.8;
  const payment = delivery ? `₩${delivery.price.toLocaleString()}` : "₩12,000";

  return (
    <>
      <div className="flex items-center gap-3">
        <IconChip name={delivery?.icon ?? "document"} size={44} />
        <div className="min-w-0 flex-1">
          <p className="text-lg font-bold tracking-[-0.4px] text-navy-900">
            {delivery?.title ?? "서류 배송"}
          </p>
          <p className="text-2xs text-muted">
            {delivery?.code ?? "#B-771"} · {delivery?.time ?? "7/21 14:20"}
          </p>
        </div>
        <Badge tone="neutral">완료</Badge>
      </div>

      <RouteCard
        origin={delivery?.pickup ?? "A동 102호"}
        destination={delivery?.dropoff ?? "B동 405호"}
      />

      <Card className="flex flex-col gap-2.5">
        <InfoRow label="물품 유형">{itemTypeLabel}</InfoRow>
        <InfoRow label="요청 부르미">
          '{counterpart}'
          <Icon name="star" size={12} className="text-teal-700" />
          {counterpartRating.toFixed(1)}
        </InfoRow>
        <InfoRow label="소요 시간">8분</InfoRow>
        <div className="mt-1 flex items-center justify-between border-t border-track pt-3">
          <span className="text-sm text-muted">결제 금액</span>
          <span className="text-lg font-bold text-navy-900">{payment}</span>
        </div>
      </Card>

      <Card variant="accent" className="flex flex-col gap-3 border-teal-500">
        <p className="text-center text-base font-bold text-navy-900">
          부르미 '{counterpart}'님은 어떠셨나요?
        </p>
        <StarRating value={rating} onChange={setRating} />
        <Button variant="navy" block disabled={rating === 0}>
          평가 남기기
        </Button>
      </Card>
    </>
  );
}

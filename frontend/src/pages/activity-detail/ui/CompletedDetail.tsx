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

/** 완료된 배달의 상세 내용(요약·경로·결제 정보·평가). */
export function CompletedDetail() {
  const [rating, setRating] = useState(0);

  return (
    <>
      <div className="flex items-center gap-3">
        <IconChip name="document" size={44} />
        <div className="min-w-0 flex-1">
          <p className="text-lg font-bold tracking-[-0.4px] text-navy-900">
            서류 배송
          </p>
          <p className="text-2xs text-muted">#B-771 · 7/21 14:20</p>
        </div>
        <Badge tone="neutral">완료</Badge>
      </div>

      <RouteCard origin="A동 102호" destination="B동 405호" />

      <Card className="flex flex-col gap-2.5">
        <InfoRow label="물품 유형">서류 · 소형(S)</InfoRow>
        <InfoRow label="요청 부르미">
          '민'
          <Icon name="star" size={12} className="text-teal-700" />
          4.8
        </InfoRow>
        <InfoRow label="소요 시간">8분</InfoRow>
        <div className="mt-1 flex items-center justify-between border-t border-track pt-3">
          <span className="text-sm text-muted">결제 금액</span>
          <span className="text-lg font-bold text-navy-900">₩12,000</span>
        </div>
      </Card>

      <Card variant="accent" className="flex flex-col gap-3 border-teal-500">
        <p className="text-center text-base font-bold text-navy-900">
          부르미 '민'님은 어떠셨나요?
        </p>
        <StarRating value={rating} onChange={setRating} />
        <Button variant="navy" block disabled={rating === 0}>
          평가 남기기
        </Button>
      </Card>

      <div className="flex gap-3">
        <Button variant="outline" block className="border-transparent bg-track">
          영수증
        </Button>
        <Button variant="outline" block className="border-transparent bg-track">
          문의하기
        </Button>
      </div>
    </>
  );
}

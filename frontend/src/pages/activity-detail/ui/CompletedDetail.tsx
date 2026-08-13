import {
  Badge,
  Button,
  Card,
  Icon,
  IconChip,
  InfoRow,
  RouteCard,
  toneForStatus,
} from "@/shared/ui";
import { useDeliveryCompletion } from "@/shared/lib/delivery/useDeliveryCompletion";
import { useReceivedReview } from "@/shared/lib/delivery/useReceivedReview";
import type { BoormiOrder } from "@/shared/store/boormiOrderAdapter";

export interface CompletedDetailProps {
  order: BoormiOrder;
}

/** 완료·취소된 배달의 상세 내용(요약·경로·결제 정보). 담당 드리미 이름·평점·소요시간은
 * 목록 요약(OrderSummaryDto)엔 없어 완료 요약 API(getDeliveryCompletion)로 보충한다. */
export function CompletedDetail({ order }: CompletedDetailProps) {
  const completion = useDeliveryCompletion(order.id);
  const { review } = useReceivedReview(order.id);
  const dreamiName = completion?.dreamiName;
  const durationLabel =
    completion?.durationMinutes != null
      ? `${completion.durationMinutes}분`
      : null;
  const payment = `₩${order.amount.toLocaleString()}`;
  // ActivityItem 목록의 tone 규칙과 동일하게: "완료"는 예외적으로 neutral로 표시한다.
  const tone =
    order.statusLabel === "완료"
      ? "neutral"
      : toneForStatus(order.statusLabel);

  return (
    <>
      <div className="flex items-center gap-3">
        <IconChip name={order.icon} size={44} />
        <div className="min-w-0 flex-1">
          <p className="text-lg font-bold tracking-[-0.4px] text-navy-900">
            {order.title}
          </p>
          <p className="text-2xs text-muted">{order.time}</p>
        </div>
        <Badge tone={tone}>{order.statusLabel}</Badge>
      </div>

      {completion?.deliveryPhotoUrl ? (
        <img
          src={completion.deliveryPhotoUrl}
          alt="배달 완료 사진"
          className="h-[210px] w-full rounded-md object-cover"
        />
      ) : (
        <div className="flex h-[210px] flex-col items-center justify-center gap-1.5 rounded-md bg-track text-muted">
          <Icon name="camera" size={22} />
          <span className="text-2xs">배달 완료 사진 없음</span>
        </div>
      )}

      <RouteCard
        origin={order.originAddress || "출발지"}
        destination={order.destinationAddress || "도착지"}
      />

      <Card className="flex flex-col gap-2.5">
        {dreamiName && (
          <InfoRow label="담당 드리미">'{dreamiName}'</InfoRow>
        )}
        {durationLabel && <InfoRow label="소요 시간">{durationLabel}</InfoRow>}
        <div className="mt-1 flex items-center justify-between border-t border-track pt-3">
          <span className="text-sm text-muted">결제 금액</span>
          <span className="text-lg font-bold text-navy-900">{payment}</span>
        </div>
      </Card>

      <Card className="flex flex-col gap-2">
        <p className="text-sm font-bold text-navy-900">드리미가 남긴 리뷰</p>
        {review ? (
          <>
            <div className="flex items-center gap-1">
              <Icon name="star" size={14} className="text-teal-700" />
              <span className="text-sm font-bold text-navy-900">
                {review.score}점
              </span>
            </div>
            <p className="text-sm text-muted">
              {review.content ?? "별점만 남겼어요."}
            </p>
          </>
        ) : (
          <p className="text-sm text-muted">아직 리뷰가 없어요.</p>
        )}
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

import { useSearchParams } from "react-router-dom";
import { useBackOrHome } from "@/shared/lib/navigation/useBackOrHome";
import {
  Badge,
  Button,
  Card,
  Icon,
  IconChip,
  InfoRow,
  RouteCard,
  ScreenShell,
  TopBar,
  toneForStatus,
} from "@/shared/ui";
import { useDreamiOrderById } from "@/shared/store/dreamiOrderStore";
import { useDeliveryCompletion } from "@/shared/lib/delivery/useDeliveryCompletion";
import { useReceivedReview } from "@/shared/lib/delivery/useReceivedReview";

/**
 * 드림 내역 상세 화면(Figma node 191:151).
 * 드리미가 수행한 배달의 정산 내역을 보여줍니다. ?id=<주문 id>로 특정 배달을 구독합니다.
 * 부르미 이름·인증사진·소요시간은 목록 요약(OrderSummaryDto)엔 없어 완료 요약 API
 * (getDeliveryCompletion)로 보충한다.
 */
export function ActivityDetailDriverScreen() {
  const backOrHome = useBackOrHome();
  const [params] = useSearchParams();
  const id = params.get("id");
  const { order, loading: deliveriesLoading } = useDreamiOrderById(id);
  const completion = useDeliveryCompletion(id);
  const { review } = useReceivedReview(id);

  const boormiName = completion?.boormiName;
  const durationLabel =
    completion?.durationMinutes != null
      ? `${completion.durationMinutes}분`
      : null;
  const payment = order ? `₩${order.amount.toLocaleString()}` : null;
  const tone = order
    ? order.statusLabel === "완료"
      ? "neutral"
      : toneForStatus(order.statusLabel)
    : "neutral";

  return (
    <ScreenShell>
      <TopBar title="드림상세" onBack={backOrHome} actions={["more"]} />

      <main className="flex flex-1 flex-col gap-4 pt-4">
        {order ? (
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
                alt="배송 완료 사진"
                className="h-[210px] w-full rounded-md object-cover"
              />
            ) : (
              <div className="flex h-[210px] flex-col items-center justify-center gap-1.5 rounded-md bg-track text-muted">
                <Icon name="camera" size={22} />
                <span className="text-2xs">배송 완료 사진 없음</span>
              </div>
            )}

            <RouteCard
              origin={order.originAddress || "출발지"}
              destination={order.destinationAddress || "도착지"}
            />

            <Card className="flex flex-col gap-2.5">
              {boormiName && (
                <InfoRow label="요청 부르미">'{boormiName}'</InfoRow>
              )}
              {durationLabel && (
                <InfoRow label="소요 시간">{durationLabel}</InfoRow>
              )}
              <div className="mt-1 flex items-center justify-between border-t border-track pt-3">
                <span className="text-sm text-muted">정산 금액</span>
                <span className="text-lg font-bold text-navy-900">
                  {payment}
                </span>
              </div>
            </Card>

            <Card className="flex flex-col gap-2">
              <p className="text-sm font-bold text-navy-900">부르미가 남긴 리뷰</p>
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
              <Button
                variant="outline"
                block
                className="border-transparent bg-track"
              >
                영수증
              </Button>
              <Button
                variant="outline"
                block
                className="border-transparent bg-track"
              >
                문의하기
              </Button>
            </div>
          </>
        ) : deliveriesLoading ? (
          <p className="py-10 text-center text-sm text-muted">불러오는 중…</p>
        ) : (
          <p className="py-10 text-center text-sm text-muted">
            내역을 찾을 수 없어요.
          </p>
        )}
      </main>
    </ScreenShell>
  );
}

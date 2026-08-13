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
  toneForStatus,
} from "@/shared/ui";
import { useDeliveryCompletion } from "@/shared/lib/delivery/useDeliveryCompletion";
import { useReceivedReview } from "@/shared/lib/delivery/useReceivedReview";
import { useMyReview } from "@/shared/lib/delivery/useMyReview";
import { api, isApiError, OrderSummaryDtoOrderCd } from "@/shared/api";
import type { BoormiOrder } from "@/shared/store/boormiOrderAdapter";

/** 리뷰 내용 최대 길이. 백엔드 ReviewContentRequest 의 @Size(max = 200) 과 맞춘다. */
const CONTENT_MAX = 200;

export interface CompletedDetailProps {
  order: BoormiOrder;
}

/** 완료·취소된 배달의 상세 내용(요약·경로·결제 정보). 담당 드리미 이름·평점·소요시간은
 * 목록 요약(OrderSummaryDto)엔 없어 완료 요약 API(getDeliveryCompletion)로 보충한다. */
export function CompletedDetail({ order }: CompletedDetailProps) {
  const completion = useDeliveryCompletion(order.id);
  const { review } = useReceivedReview(order.id);
  const { review: myReview, loading: myReviewLoading } = useMyReview(
    order.id,
  );

  // 별점은 한 번 남기면 서버가 다시 못 남기게 막는다(ALREADY_REVIEWED) — 이번 세션에 막 등록한
  // 값을 훅이 다시 조회해오기 전까지 optimistic하게 반영한다. 내용(content)은 몇 번이든
  // 수정할 수 있어 같은 방식으로 최신값을 덮어쓴다.
  const [submittedScore, setSubmittedScore] = useState<number | null>(null);
  const [submittedContent, setSubmittedContent] = useState<string | null>(
    null,
  );
  const [rating, setRating] = useState(0);
  const [editingContent, setEditingContent] = useState(false);
  const [content, setContent] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [reviewError, setReviewError] = useState<string | null>(null);

  const myScore = submittedScore ?? myReview?.score ?? null;
  const myContent = submittedContent ?? myReview?.content ?? null;
  const hasScored = myScore != null;

  const onSubmitScore = async () => {
    if (rating === 0) return;
    setSubmitting(true);
    setReviewError(null);
    try {
      const { result } = await api.writeScore(order.id, { score: rating });
      setSubmittedScore(result?.score ?? rating);
    } catch (e) {
      // 뒤로 갔다 다시 들어와 이미 별점을 남긴 건이면 그 값을 그대로 고정 표시로 취급한다.
      if (isApiError(e) && e.code === "ORDER_019") {
        setSubmittedScore(rating);
        return;
      }
      setReviewError(isApiError(e) ? e.message : "별점을 남기지 못했어요.");
    } finally {
      setSubmitting(false);
    }
  };

  const onSubmitContent = async () => {
    if (!content.trim()) return;
    setSubmitting(true);
    setReviewError(null);
    try {
      const { result } = await api.writeContent(order.id, {
        content: content.trim(),
      });
      setSubmittedContent(result?.content ?? content.trim());
      setEditingContent(false);
    } catch (e) {
      setReviewError(isApiError(e) ? e.message : "리뷰를 등록하지 못했어요.");
    } finally {
      setSubmitting(false);
    }
  };

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

      {order.orderCd === OrderSummaryDtoOrderCd.COMPLETED && (
        <Card className="flex flex-col gap-3">
          <p className="text-sm font-bold text-navy-900">내가 남긴 리뷰</p>

          {myReviewLoading ? (
            <p className="text-sm text-muted">확인 중…</p>
          ) : !hasScored ? (
            <>
              <StarRating value={rating} onChange={setRating} />
              <Button
                variant="navy"
                block
                disabled={rating === 0 || submitting}
                onClick={onSubmitScore}
              >
                {submitting ? "등록 중…" : "별점 남기기"}
              </Button>
            </>
          ) : (
            <>
              <StarRating value={myScore ?? 0} readOnly />
              {editingContent ? (
                <>
                  <textarea
                    rows={3}
                    maxLength={CONTENT_MAX}
                    placeholder="어떤 점이 좋았나요? (선택)"
                    value={content}
                    onChange={(e) => setContent(e.target.value)}
                    className="resize-none rounded-md bg-track px-3.5 py-3 text-md text-navy-900 outline-none placeholder:text-muted"
                  />
                  <div className="flex gap-3">
                    <Button
                      variant="navy"
                      block
                      disabled={!content.trim() || submitting}
                      onClick={onSubmitContent}
                    >
                      {submitting ? "등록 중…" : "리뷰 등록"}
                    </Button>
                    <Button
                      variant="outline"
                      block
                      disabled={submitting}
                      onClick={() => setEditingContent(false)}
                    >
                      취소
                    </Button>
                  </div>
                </>
              ) : (
                <>
                  <p className="text-sm text-muted">
                    {myContent ?? "별점만 남겼어요."}
                  </p>
                  <Button
                    variant="outline"
                    block
                    onClick={() => {
                      setContent(myContent ?? "");
                      setEditingContent(true);
                    }}
                  >
                    {myContent ? "리뷰 수정" : "리뷰 작성"}
                  </Button>
                </>
              )}
            </>
          )}

          {reviewError && (
            <p className="text-sm text-status-danger">{reviewError}</p>
          )}
        </Card>
      )}

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

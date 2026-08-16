import { useEffect, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import {
  Button,
  Card,
  Icon,
  IconChip,
  ScreenShell,
  StarRating,
  TopBar,
} from "@/shared/ui";
import { ROUTES } from "@/shared/config/routes";
import { useActiveDelivery } from "@/shared/store/deliveryStore";
import {
  api,
  isApiError,
  DeliveryCompletionDtoItemCd,
  type DeliveryCompletionDto,
} from "@/shared/api";

/** 리뷰 내용 최대 길이. 백엔드 ReviewContentRequest 의 @Size(max = 200) 과 맞춘다. */
const CONTENT_MAX = 200;

/** 물건 카테고리 코드 → 한글 라벨(request-create의 itemTypeToCd 역방향과 동일한 단어 사용). */
function itemCdToLabel(cd?: DeliveryCompletionDtoItemCd): string {
  switch (cd) {
    case DeliveryCompletionDtoItemCd.DOCUMENT:
      return "서류";
    case DeliveryCompletionDtoItemCd.PACKAGE:
      return "소형택배";
    case DeliveryCompletionDtoItemCd.SAMPLE:
      return "샘플";
    default:
      return "기타";
  }
}

/**
 * 배달 완료 화면(Figma node 191:881).
 * 완료된 활성 배달의 정산 요약을 보여주고 상대방 평가를 남깁니다.
 *
 * 실 백엔드 모드: URL에 `?orderId=`가 있으면 GET /api/v1/delivery/orders/{orderId}/completion으로
 * (추적 화면 전용 위치·경로 없이) 완료 요약만 받아온다. orderId가 없거나 조회에 실패하면 기존 mock
 * 스토어(`useActiveDelivery`) 값으로, 그마저 없으면 고정 플레이스홀더로 폴백한다.
 * 물품 사이즈(소형/중형)는 백엔드에 아직 없는 개념이라, 실 데이터 경로는 사이즈 대신 물건 카테고리를 보여준다.
 *
 * 리뷰는 별점을 먼저 등록(POST)하고, 성공하면 같은 카드에서 내용을 채운다(PATCH). 리뷰 대상(상대방)은
 * 서버가 로그인 세션으로 판별하므로 프론트는 orderId만 넘긴다. orderId가 없으면 mock 흐름이라
 * 평가 카드를 아예 노출하지 않는다.
 */
export function DeliveryCompleteScreen() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const [rating, setRating] = useState(0);
  const [content, setContent] = useState("");
  const [phase, setPhase] = useState<"score" | "content" | "done">("score");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const active = useActiveDelivery();
  const orderId = searchParams.get("orderId");
  const [detail, setDetail] = useState<DeliveryCompletionDto | null>(null);

  useEffect(() => {
    if (!orderId) return;
    let cancelled = false;
    void api
      .getDeliveryCompletion(orderId)
      .then(({ result }) => {
        if (!cancelled && result) setDetail(result);
      })
      .catch(() => {
        // 완료 요약은 참고용 정보라, 조회 실패해도 화면은 그대로 두고 폴백 값을 보여준다.
      });
    return () => {
      cancelled = true;
    };
  }, [orderId]);

  // 평가 대상: 로그인 세션 기반으로 서버가 내려준 viewerIsDreami로 판단한다(URL 파라미터는 조작 가능해 신뢰 불가).
  // 실 데이터가 없는 mock 미리보기 모드에서만 `?reviewee=boormi` 쿼리로 폴백한다.
  const reviewsBoormi =
    detail?.viewerIsDreami ?? searchParams.get("reviewee") === "boormi";
  const driverName = detail?.dreamiName ?? active?.driverName ?? "핀";
  const boormiName = detail?.boormiName ?? "부르미";
  const reviewPrompt = reviewsBoormi
    ? `부르미 '${boormiName}'님은 어떠셨나요?`
    : `드리미 '${driverName}'님은 어떠셨나요?`;
  const subtitle = reviewsBoormi
    ? `부르미 '${boormiName}'의 배송 완료`
    : `드리미 '${driverName}'이 배송 완료`;
  const sizeLabel = active?.itemSize === "M" ? "중형(M)" : "소형(S)";
  const itemTypeLabel = detail
    ? `${detail.itemName} · ${itemCdToLabel(detail.itemCd)}`
    : active
      ? `${active.itemType} · ${sizeLabel}`
      : "서류 · 소형(S)";
  const durationLabel =
    detail?.durationMinutes != null ? `${detail.durationMinutes}분` : "8분";
  const summary = [
    { label: "물품 유형", value: itemTypeLabel },
    reviewsBoormi
      ? { label: "담당 부르미", value: `'${boormiName}'` }
      : { label: "담당 드리미", value: `'${driverName}'` },
    { label: "소요 시간", value: durationLabel },
  ];
  const payment =
    detail?.deliveryAmount != null
      ? `₩${detail.deliveryAmount.toLocaleString()}`
      : active
        ? `₩${active.price.toLocaleString()}`
        : "₩12,000";

  // 별점을 먼저 등록하고(POST), 성공하면 같은 카드에서 리뷰 내용을 채운다(PATCH).
  const onSubmitScore = async () => {
    if (!orderId || rating === 0) return;
    setSubmitting(true);
    setError(null);
    try {
      await api.writeScore(orderId, { score: rating });
      setPhase("content");
    } catch (e) {
      // 뒤로가기로 다시 들어와 이미 별점을 남긴 건이면 내용 입력 단계로 넘겨준다.
      if (isApiError(e) && e.code === "ORDER_019") {
        setPhase("content");
        return;
      }
      setError(isApiError(e) ? e.message : "평가를 남기지 못했어요.");
    } finally {
      setSubmitting(false);
    }
  };

  const onSubmitContent = async () => {
    if (!orderId || !content.trim()) return;
    setSubmitting(true);
    setError(null);
    try {
      await api.writeContent(orderId, { content: content.trim() });
      setPhase("done");
    } catch (e) {
      setError(isApiError(e) ? e.message : "리뷰를 등록하지 못했어요.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <ScreenShell>
      <TopBar
        title="배달 완료"
        onBack={() => navigate(ROUTES.home, { replace: true })}
        actions={[]}
      />

      <main className="flex flex-1 flex-col gap-4 pt-4">
        <div className="flex items-center gap-3">
          <IconChip name="check" size={44} />
          <div className="flex flex-col gap-0.5">
            <h1 className="text-lg font-bold tracking-[-0.4px] text-navy-900">
              드림이 완료되었어요
            </h1>
            <p className="text-2xs text-muted">{subtitle}</p>
          </div>
        </div>

        {/* 배송 완료 사진: 조회된 인증 사진이 있으면 실제 사진, 없으면(mock/미인증) 자리표시 */}
        {detail?.deliveryPhotoUrl ? (
          <img
            src={detail.deliveryPhotoUrl}
            alt="배송 완료 사진"
            className="h-[210px] w-full rounded-md object-cover"
          />
        ) : (
          <div className="flex h-[210px] flex-col items-center justify-center gap-1.5 rounded-md bg-track text-muted">
            <Icon name="camera" size={22} />
            <span className="text-2xs">배송 완료 사진</span>
          </div>
        )}

        <Card className="flex flex-col gap-2">
          {summary.map((row) => (
            <div key={row.label} className="flex justify-between">
              <span className="text-sm text-muted">{row.label}</span>
              <span className="text-sm font-bold text-navy-900">
                {row.value}
              </span>
            </div>
          ))}
          <div className="mt-1 flex items-center justify-between border-t border-line pt-3">
            <span className="text-sm text-muted">결제 금액</span>
            <span className="text-lg font-bold text-navy-900">{payment}</span>
          </div>
        </Card>

        {orderId && (
          <Card className="flex flex-col gap-3">
            <p className="text-center text-md font-bold text-navy-900">
              {phase === "done" ? "평가 감사합니다" : reviewPrompt}
            </p>
            <StarRating
              value={rating}
              onChange={setRating}
              readOnly={phase !== "score"}
            />

            {phase === "score" && (
              <Button
                variant="navy"
                block
                disabled={rating === 0 || submitting}
                onClick={onSubmitScore}
              >
                {submitting ? "등록 중…" : "평가 남기기"}
              </Button>
            )}

            {phase === "content" && (
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
                    onClick={() => setPhase("done")}
                  >
                    건너뛰기
                  </Button>
                </div>
              </>
            )}

            {error && <p className="text-sm text-status-danger">{error}</p>}
          </Card>
        )}
      </main>

      <footer className="pt-4">
        <Button block onClick={() => navigate(ROUTES.home, { replace: true })}>
          완료하기
        </Button>
      </footer>
    </ScreenShell>
  );
}

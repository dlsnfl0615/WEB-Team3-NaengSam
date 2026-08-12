import { useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { Button, Card, Icon, IconChip, ScreenShell, TopBar } from "@/shared/ui";
import { api, isApiError } from "@/shared/api";
import { ROUTES } from "@/shared/config/routes";
import { useActiveDelivery } from "@/shared/store/deliveryStore";
import { StarRating } from "./StarRating";

/** 리뷰 내용 최대 길이. 백엔드 ReviewContentRequest 의 @Size(max = 200) 과 맞춘다. */
const CONTENT_MAX = 200;

/**
 * 배달 완료 화면(Figma node 191:881).
 * 완료된 활성 배달의 정산 요약을 보여주고 드리미 평가를 남깁니다.
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

  // 리뷰 대상(상대방)은 서버가 로그인 세션으로 판별하므로 프론트는 orderId 만 넘긴다.
  // orderId 가 없으면 mock 흐름이라 평가 카드를 아예 노출하지 않는다.
  const orderId = searchParams.get("orderId");
  // 평가 대상: 기본은 드리미(부르미가 드리미를 평가), `?reviewee=boormi`면 드리미가 부르미를 평가.
  const reviewsBoormi = searchParams.get("reviewee") === "boormi";
  const driverName = active?.driverName ?? "핀";
  const reviewPrompt = reviewsBoormi
    ? "부르미님은 어떠셨나요?"
    : `드리미 '${driverName}'님은 어떠셨나요?`;
  const sizeLabel = active?.itemSize === "M" ? "중형(M)" : "소형(S)";
  const summary = [
    {
      label: "물품 유형",
      value: active ? `${active.itemType} · ${sizeLabel}` : "서류 · 소형(S)",
    },
    { label: "담당 드리미", value: `'${driverName}'` },
    { label: "소요 시간", value: "8분" },
  ];
  const payment = active ? `₩${active.price.toLocaleString()}` : "₩12,000";

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
            <p className="text-2xs text-muted">
              드리미 '{driverName}'이 배송 완료
            </p>
          </div>
        </div>

        {/* 배송 완료 사진(자리표시) */}
        <div className="flex h-[210px] flex-col items-center justify-center gap-1.5 rounded-md bg-track text-muted">
          <Icon name="camera" size={22} />
          <span className="text-2xs">배송 완료 사진</span>
        </div>

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

import { useEffect, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { Button, Card, Icon, IconChip, ScreenShell, TopBar } from "@/shared/ui";
import { ROUTES } from "@/shared/config/routes";
import { useActiveDelivery } from "@/shared/store/deliveryStore";
import {
  api,
  DeliveryCompletionDtoItemCd,
  type DeliveryCompletionDto,
} from "@/shared/api";
import { StarRating } from "./StarRating";

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
 * 완료된 활성 배달의 정산 요약을 보여주고 드리미 평가를 남깁니다.
 *
 * 실 백엔드 모드: URL에 `?orderId=`가 있으면 GET /api/v1/delivery/orders/{orderId}/completion으로
 * (추적 화면 전용 위치·경로 없이) 완료 요약만 받아온다. orderId가 없거나 조회에 실패하면 기존 mock
 * 스토어(`useActiveDelivery`) 값으로, 그마저 없으면 고정 플레이스홀더로 폴백한다.
 * 물품 사이즈(소형/중형)는 백엔드에 아직 없는 개념이라, 실 데이터 경로는 사이즈 대신 물건 카테고리를 보여준다.
 */
export function DeliveryCompleteScreen() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const [rating, setRating] = useState(0);
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

  // 평가 대상: 기본은 드리미(부르미가 드리미를 평가), `?reviewee=boormi`면 드리미가 부르미를 평가.
  const reviewsBoormi = searchParams.get("reviewee") === "boormi";
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

        <Card className="flex flex-col gap-3">
          <p className="text-center text-md font-bold text-navy-900">
            {reviewPrompt}
          </p>
          <StarRating value={rating} onChange={setRating} />
          <Button variant="navy" block>
            평가 남기기
          </Button>
        </Card>
      </main>

      <footer className="pt-4">
        <Button block onClick={() => navigate(ROUTES.home, { replace: true })}>
          완료하기
        </Button>
      </footer>
    </ScreenShell>
  );
}

import { Button, Card } from "@/shared/ui";
import type { RequestForm } from "./types";

export interface StepPaymentProps {
  form: RequestForm;
  /** 포인트 충전 화면으로 이동(미구현 시 no-op) */
  onCharge?: () => void;
}

/** 스텝 4: 결제 — 결제 예정 금액·보유 포인트 요약(UI 전용). */
export function StepPayment({ form, onCharge }: StepPaymentProps) {
  const sizeLabel = form.itemSize === "S" ? "소형 (S)" : "중형 (M)";

  return (
    <div className="flex flex-col gap-4">
      <Card variant="hero" className="flex flex-col gap-4">
        <div className="flex flex-col gap-1">
          <p className="text-xs text-track">결제 예정 금액</p>
          <p className="flex items-baseline gap-2">
            <span className="text-xl font-bold text-white">12,000 P</span>
            <span className="text-xs text-track">VAT 포함</span>
          </p>
        </div>

        <div className="flex flex-col gap-1.5 text-xs text-track">
          <div className="flex justify-between">
            <span>서비스 항목</span>
            <span>{form.itemType} 배송</span>
          </div>
          <div className="flex justify-between">
            <span>물품 크기</span>
            <span>{sizeLabel}</span>
          </div>
        </div>

        <div className="flex flex-col gap-1">
          <p className="text-center text-xs text-track">보유 포인트</p>
          <p className="text-xl font-bold text-white">12,400 P</p>
        </div>

        <Button variant="primary" block arrow onClick={onCharge}>
          포인트 충전
        </Button>
      </Card>
    </div>
  );
}

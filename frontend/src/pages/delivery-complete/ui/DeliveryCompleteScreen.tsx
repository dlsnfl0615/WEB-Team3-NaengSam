import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { Button, Card, Icon, IconChip, ScreenShell, TopBar } from "@/shared/ui";
import { ROUTES } from "@/shared/config/routes";
import { StarRating } from "./StarRating";

const SUMMARY = [
  { label: "물품 유형", value: "서류 · 소형(S)" },
  { label: "담당 드리미", value: "'핀'" },
  { label: "소요 시간", value: "8분" },
];

/**
 * 배달 완료 화면(Figma node 191:881).
 * 완료 사진·정산 요약을 보여주고 드리미 평가를 남깁니다(UI 전용).
 */
export function DeliveryCompleteScreen() {
  const navigate = useNavigate();
  const [rating, setRating] = useState(0);

  return (
    <ScreenShell>
      <TopBar title="배달 완료" onBack={() => navigate(-1)} actions={[]} />

      <main className="flex flex-1 flex-col gap-4 pt-4">
        <div className="flex items-center gap-3">
          <IconChip name="check" size={44} />
          <div className="flex flex-col gap-0.5">
            <h1 className="text-lg font-bold tracking-[-0.4px] text-navy-900">
              드림이 완료되었어요
            </h1>
            <p className="text-2xs text-muted">드리미 '핀'이 배송 완료</p>
          </div>
        </div>

        {/* 배송 완료 사진(자리표시) */}
        <div className="flex h-[210px] flex-col items-center justify-center gap-1.5 rounded-md border border-dashed border-line bg-canvas text-muted">
          <Icon name="camera" size={22} />
          <span className="text-2xs">배송 완료 사진</span>
        </div>

        <Card className="flex flex-col gap-2">
          {SUMMARY.map((row) => (
            <div key={row.label} className="flex justify-between">
              <span className="text-sm text-muted">{row.label}</span>
              <span className="text-sm font-bold text-navy-900">
                {row.value}
              </span>
            </div>
          ))}
          <div className="mt-1 flex items-center justify-between border-t border-line pt-3">
            <span className="text-sm text-muted">결제 금액</span>
            <span className="text-lg font-bold text-navy-900">₩12,000</span>
          </div>
        </Card>

        <Card className="flex flex-col gap-3">
          <p className="text-center text-md font-bold text-navy-900">
            드리미 '핀'님은 어떠셨나요?
          </p>
          <StarRating value={rating} onChange={setRating} />
          <Button variant="navy" block>
            평가 남기기
          </Button>
        </Card>
      </main>

      <footer className="pt-4">
        <Button block onClick={() => navigate(ROUTES.home)}>
          완료하기
        </Button>
      </footer>
    </ScreenShell>
  );
}

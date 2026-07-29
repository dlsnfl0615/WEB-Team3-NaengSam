import { useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  Badge,
  Button,
  Card,
  IconChip,
  InfoRow,
  ScreenShell,
  StarRating,
  TopBar,
} from "@/shared/ui";
import { ProofCarousel } from "./ProofCarousel";

/**
 * 드림 내역 상세 화면(Figma node 191:151).
 * 드리미가 수행한 배달의 인증 사진·정산 내역·부르미 평가를 보여줍니다.
 */
export function ActivityDetailDriverScreen() {
  const navigate = useNavigate();
  const [rating, setRating] = useState(0);

  return (
    <ScreenShell>
      <TopBar title="드림상세" onBack={() => navigate(-1)} actions={["more"]} />

      <main className="flex flex-1 flex-col gap-4 pt-4">
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

        <ProofCarousel />

        <Card className="flex flex-col gap-2.5">
          <div className="flex items-center justify-between">
            <div className="flex flex-col">
              <span className="text-2xs text-muted">출발지</span>
              <span className="text-md font-bold text-navy-900">A동 102호</span>
            </div>
            <span className="flex gap-1" aria-hidden>
              {[0, 1, 2, 3].map((dot) => (
                <span key={dot} className="size-1 rounded-pill bg-track" />
              ))}
            </span>
            <div className="flex flex-col text-right">
              <span className="text-2xs text-muted">도착지</span>
              <span className="text-md font-bold text-navy-900">B동 405호</span>
            </div>
          </div>

          <InfoRow label="물품 유형">서류 · 소형(S)</InfoRow>
          <InfoRow label="담당 드리미">'핀'</InfoRow>
          <InfoRow label="소요 시간">8분</InfoRow>

          <div className="mt-1 flex items-center justify-between border-t border-track pt-3">
            <span className="text-sm text-muted">결제 금액</span>
            <span className="text-lg font-bold text-navy-900">₩12,000</span>
          </div>
        </Card>

        <Card className="flex flex-col gap-3">
          <p className="text-center text-base font-bold text-navy-900">
            부르미 '핀'님은 어떠셨나요?
          </p>
          <StarRating value={rating} onChange={setRating} />
          <Button variant="navy" block disabled={rating === 0}>
            평가 남기기
          </Button>
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
      </main>
    </ScreenShell>
  );
}

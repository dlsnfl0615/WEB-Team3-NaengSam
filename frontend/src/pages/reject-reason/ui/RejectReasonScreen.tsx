import { useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  Button,
  Icon,
  IconChip,
  RadioOption,
  ScreenShell,
  TopBar,
} from "@/shared/ui";
import { ROUTES } from "@/shared/config/routes";

const REASONS = [
  "별점이 낮아요",
  "픽업 거리가 멀어요",
  "다른 드리미를 기다릴게요",
  "기타",
] as const;

/**
 * 드리미 거절 사유 선택 화면(Figma node 191:731).
 * 사유를 하나만 고르고 기타 사유를 덧붙여 거절합니다(UI 전용).
 */
export function RejectReasonScreen() {
  const navigate = useNavigate();
  const [reason, setReason] = useState<(typeof REASONS)[number]>(REASONS[0]);
  const [etc, setEtc] = useState("");

  return (
    <ScreenShell>
      <TopBar title="거절 사유 선택" onBack={() => navigate(-1)} actions={[]} />

      <main className="flex flex-1 flex-col gap-4 pt-4">
        <h1 className="text-lg font-bold tracking-[-0.4px] text-navy-900">
          이 드리미를
          <br />
          거절하는 이유를 알려주세요
        </h1>

        <div className="flex items-center gap-2 border-b border-line pb-4">
          <IconChip name="bell" size={36} />
          <div className="flex flex-col">
            <p className="text-md font-bold text-navy-900">드리미 '핀'</p>
            <p className="flex items-center gap-1 text-2xs font-semibold text-navy-900">
              <Icon name="star" size={12} className="text-status-warning" />
              4.9 · 배송 132건
            </p>
          </div>
        </div>

        <div role="radiogroup" className="flex flex-col gap-2">
          {REASONS.map((option) => (
            <RadioOption
              key={option}
              label={option}
              selected={reason === option}
              onSelect={() => setReason(option)}
            />
          ))}
        </div>

        <textarea
          rows={3}
          placeholder="기타 사유를 입력해 주세요"
          value={etc}
          onChange={(e) => setEtc(e.target.value)}
          className="resize-none rounded-md bg-track px-3.5 py-3 text-md text-navy-900 outline-none placeholder:text-muted"
        />
      </main>

      <footer className="flex gap-2 pt-4">
        <Button variant="outline" onClick={() => navigate(-1)}>
          이전
        </Button>
        <Button
          variant="navy"
          block
          disabled={reason === "기타" && !etc.trim()}
          onClick={() => navigate(ROUTES.matching)}
        >
          사유 제출하고 거절
        </Button>
      </footer>
    </ScreenShell>
  );
}

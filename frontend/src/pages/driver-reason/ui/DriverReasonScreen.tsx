import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { useBackOrHome } from "@/shared/lib/navigation/useBackOrHome";
import { Button, RadioOption, ScreenShell, TopBar } from "@/shared/ui";
import { ROUTES } from "@/shared/config/routes";
import { useDeliveryStore } from "@/shared/store/deliveryStore";

const REASONS = [
  "사고가 났어요",
  "시간 안에 가지 못할 것 같아요",
  "물품을 분실했어요",
  "기타",
] as const;

/**
 * 드리미 배송 사유 선택 화면(Figma node 191:1298).
 * 배송 중 발생한 사고·지연 사유를 골라 제출하면 활성 배달을 사고/취소 처리합니다.
 */
export function DriverReasonScreen() {
  const backOrHome = useBackOrHome();
  const navigate = useNavigate();
  const cancel = useDeliveryStore((s) => s.cancel);
  const [reason, setReason] = useState<(typeof REASONS)[number]>(REASONS[0]);
  const [etc, setEtc] = useState("");
  const [submitting, setSubmitting] = useState(false);

  const onSubmit = async () => {
    setSubmitting(true);
    try {
      await cancel(etc.trim() ? `${reason} (${etc.trim()})` : reason);
      navigate(ROUTES.home, { replace: true });
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <ScreenShell>
      <TopBar title="사유 선택" onBack={backOrHome} actions={[]} />

      <main className="flex flex-1 flex-col gap-4 pt-4">
        <h1 className="border-b border-line pb-4 text-lg font-bold tracking-[-0.4px] text-navy-900">
          무슨 일이신가요?
        </h1>

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

      <footer className="pt-4">
        <Button
          variant="navy"
          block
          disabled={submitting || (reason === "기타" && !etc.trim())}
          onClick={onSubmit}
        >
          {submitting ? "제출 중…" : "사고 사유 제출하기"}
        </Button>
      </footer>
    </ScreenShell>
  );
}

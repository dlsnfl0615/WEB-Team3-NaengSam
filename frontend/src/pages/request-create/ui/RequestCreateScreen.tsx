import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { Button, ScreenShell, TopBar } from "@/shared/ui";
import { ROUTES } from "@/shared/config/routes";
import { RequestStepper } from "./RequestStepper";
import { StepLocation } from "./StepLocation";
import { StepItem } from "./StepItem";
import { StepPhoto } from "./StepPhoto";
import { StepPayment } from "./StepPayment";
import type { RequestForm } from "./types";

const INITIAL_FORM: RequestForm = {
  pickup: "",
  dropoff: "",
  meeting: "대면",
  itemType: "서류",
  itemSize: "S",
  itemName: "",
  detail: "",
  tags: ["없음"],
  etc: "",
};

/**
 * 부름 등록 화면(Figma node 191:548/475/416/340).
 * 위치 → 물품 → 사진·요청 → 결제 4단계 멀티스텝 폼(UI 전용, API 미연동).
 */
export function RequestCreateScreen() {
  const navigate = useNavigate();
  const [step, setStep] = useState(1);
  const [form, setForm] = useState<RequestForm>(INITIAL_FORM);

  const update = (patch: Partial<RequestForm>) =>
    setForm((prev) => ({ ...prev, ...patch }));

  const next = () => setStep((s) => Math.min(4, s + 1));
  const prev = () => setStep((s) => Math.max(1, s - 1));
  const back = () => (step > 1 ? prev() : navigate(-1));

  return (
    <ScreenShell>
      <TopBar title="부름 등록" onBack={back} />

      <div className="pt-4">
        <RequestStepper current={step} />
      </div>

      <main className="flex flex-1 flex-col pt-5">
        {step === 1 && <StepLocation form={form} update={update} />}
        {step === 2 && <StepItem form={form} update={update} />}
        {step === 3 && <StepPhoto form={form} update={update} />}
        {step === 4 && <StepPayment form={form} />}
      </main>

      <footer className="pt-4">
        {step === 1 && (
          <Button variant="navy" block arrow onClick={next}>
            계속하기
          </Button>
        )}
        {(step === 2 || step === 3) && (
          <div className="flex gap-3">
            <Button variant="outline" className="px-7" onClick={prev}>
              이전
            </Button>
            <Button variant="navy" arrow className="flex-1" onClick={next}>
              다음으로
            </Button>
          </div>
        )}
        {step === 4 && (
          <Button
            variant="navy"
            block
            arrow
            onClick={() => navigate(ROUTES.home)}
          >
            등록 및 결제하기
          </Button>
        )}
      </footer>
    </ScreenShell>
  );
}

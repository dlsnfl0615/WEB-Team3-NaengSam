import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useBackOrHome } from "@/shared/lib/navigation/useBackOrHome";
import { Button, ScreenShell, TopBar } from "@/shared/ui";
import { ROUTES } from "@/shared/config/routes";
import { api, isApiError, type ExpectedValueDto } from "@/shared/api";
import { useBoormiOrderStore } from "@/shared/store/boormiOrderStore";
import { RequestStepper } from "./RequestStepper";
import { StepLocation } from "./StepLocation";
import { StepItem } from "./StepItem";
import { StepPhoto } from "./StepPhoto";
import { StepPayment } from "./StepPayment";
import { itemTypeToCd, toOrderRequest } from "./orderRequest";
import {
  clearRequestDraft,
  readRequestDraft,
  saveRequestDraft,
} from "./requestDraft";
import type { RequestForm } from "./types";

const INITIAL_FORM: RequestForm = {
  pickup: "",
  pickupDetail: "",
  dropoff: "",
  dropoffDetail: "",
  // 전달 방식은 비대면 고정(대면 미지원) — 선택 UI는 AddressSheet에서 숨김 처리.
  pickupMeeting: "비대면",
  dropoffMeeting: "비대면",
  itemType: "서류",
  itemSize: "S",
  itemName: "",
  detail: "",
  requestTag: "없음",
  etc: "",
};

/**
 * 부름 등록 화면(Figma node 191:548/475/416/340).
 * 위치 → 물품 → 사진·요청 → 결제 4단계 멀티스텝 폼.
 * 주소·예상요금·이미지 업로드·콜 등록을 실제 부르미 API로 연동한다.
 */
export function RequestCreateScreen() {
  const backOrHome = useBackOrHome();
  const navigate = useNavigate();
  const createOrder = useBoormiOrderStore((s) => s.createOrder);
  // 포인트 충전 등으로 화면을 떠났다 돌아오면 작성 중이던 내용을 그대로 복원한다.
  const [draft] = useState(readRequestDraft);
  const [step, setStep] = useState(draft?.step ?? 1);
  const [form, setForm] = useState<RequestForm>(draft?.form ?? INITIAL_FORM);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [estimate, setEstimate] = useState<ExpectedValueDto | null>(null);
  const [estimating, setEstimating] = useState(false);

  const update = (patch: Partial<RequestForm>) => {
    setError(null);
    if (
      patch.pickup !== undefined ||
      patch.dropoff !== undefined ||
      patch.itemType !== undefined
    ) {
      setEstimate(null);
    }
    setForm((prev) => ({ ...prev, ...patch }));
  };

  const next = () => setStep((s) => Math.min(4, s + 1));
  const prev = () => setStep((s) => Math.max(1, s - 1));
  // 첫 스텝에서 뒤로 = 등록 포기 → 임시저장도 비운다(충전 등으로 잠깐 나가는 경우와 구분).
  const back = () => {
    if (step > 1) {
      prev();
      return;
    }
    clearRequestDraft();
    backOrHome();
  };

  // 스텝·입력이 바뀔 때마다 스냅샷을 남긴다. 견적은 서버 파생값이라 저장하지 않고 복원 후 재조회한다.
  useEffect(() => {
    saveRequestDraft({ step, form });
  }, [step, form]);

  // 출발·도착지와 물품 유형이 준비되면 예상 요금을 실시간 조회.
  useEffect(() => {
    let cancelled = false;
    const run = async () => {
      if (!form.pickup.trim() || !form.dropoff.trim()) {
        setEstimate(null);
        return;
      }
      setEstimating(true);
      try {
        const { result } = await api.expectedValue({
          originAddressLine1: form.pickup.trim(),
          destinationAddressLine1: form.dropoff.trim(),
          itemCd: itemTypeToCd(form.itemType),
        });
        if (!cancelled) {
          setEstimate(result ?? null);
          setError(
            result ? null : "예상 배송 요금을 확인하지 못했어요.",
          );
        }
      } catch (e) {
        if (!cancelled) {
          setEstimate(null);
          setError(
            isApiError(e)
              ? e.message
              : "예상 배송 요금을 계산하지 못했어요. 다시 시도해주세요.",
          );
        }
      } finally {
        if (!cancelled) setEstimating(false);
      }
    };
    run();
    return () => {
      cancelled = true;
    };
  }, [form.pickup, form.dropoff, form.itemType]);

  // 스텝별 필수값과 견적 조회 성공 여부를 검증한다.
  const hasValidEstimate = estimate !== null && !estimating;
  const canProceed =
    step === 1
      ? !!form.pickup.trim() && !!form.dropoff.trim() && hasValidEstimate
      : step === 2
        ? hasValidEstimate
        : step === 3
          ? !!form.itemName.trim()
          : true;

  const submit = async () => {
    setSubmitting(true);
    setError(null);
    try {
      const orderId = await createOrder(toOrderRequest(form));
      if (!orderId) {
        setError("등록한 부름 정보를 확인하지 못했어요. 다시 시도해주세요.");
        return;
      }
      clearRequestDraft();
      // 생성된 부름을 식별할 수 있도록 주문 ID와 함께 드리미 대기 화면으로 이동한다.
      navigate(`${ROUTES.matching}?orderId=${orderId}`, { replace: true });
    } catch (e) {
      setError(
        isApiError(e) ? e.message : "콜 등록에 실패했어요. 다시 시도해주세요.",
      );
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <ScreenShell>
      <TopBar title="부름 등록" onBack={back} actions={["profile"]} />

      <div className="pt-4">
        <RequestStepper current={step} />
      </div>

      <main className="flex flex-1 flex-col pt-5">
        {step === 1 && <StepLocation form={form} update={update} />}
        {step === 2 && (
          <StepItem
            form={form}
            update={update}
            estimate={estimate}
            estimating={estimating}
          />
        )}
        {step === 3 && <StepPhoto form={form} update={update} />}
        {step === 4 && (
          <StepPayment
            form={form}
            estimate={estimate}
            onCharge={() => navigate(ROUTES.pointCharge)}
          />
        )}
      </main>

      <footer className="pt-4">
        {error && (
          <p className="pb-3 text-center text-sm text-status-danger">{error}</p>
        )}
        {step === 1 && (
          <Button
            variant="navy"
            block
            arrow
            disabled={!canProceed}
            onClick={next}
          >
            계속하기
          </Button>
        )}
        {(step === 2 || step === 3) && (
          <div className="flex gap-3">
            <Button variant="outline" className="px-7" onClick={prev}>
              이전
            </Button>
            <Button
              variant="navy"
              arrow
              className="flex-1"
              disabled={!canProceed}
              onClick={next}
            >
              다음으로
            </Button>
          </div>
        )}
        {step === 4 && (
          <Button
            variant="navy"
            block
            arrow
            disabled={submitting}
            onClick={submit}
          >
            {submitting ? "등록 중…" : "등록 및 결제하기"}
          </Button>
        )}
      </footer>
    </ScreenShell>
  );
}

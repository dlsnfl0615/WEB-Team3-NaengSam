import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { Button, Card, RadioOption, TextField } from "@/shared/ui";
import { isApiError } from "@/shared/api";
import { useWalletStore } from "@/shared/store/walletStore";

const QUICK_AMOUNTS = [1000, 5000, 10000];

/** 백엔드 PointChargeRequest 제약(@Min/@Max)과 맞춘 충전 한도. */
const MIN_AMOUNT = 1000;
const MAX_AMOUNT = 1000000;

/** 카드 결제로 포인트를 충전하는 본문. */
export function ChargeForm() {
  const navigate = useNavigate();
  const currentPoints = useWalletStore((s) => s.points);
  const charge = useWalletStore((s) => s.charge);
  const load = useWalletStore((s) => s.load);
  const [amount, setAmount] = useState(5000);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // 지갑 화면을 거치지 않고 들어와도 "현재 보유" 포인트를 보여주기 위해 한 번 조회한다.
  useEffect(() => {
    load();
  }, [load]);

  const add = (value: number) =>
    setAmount((prev) => Math.min(prev + value, MAX_AMOUNT));
  const formatted = amount.toLocaleString();
  const outOfRange = amount < MIN_AMOUNT || amount > MAX_AMOUNT;

  const onCharge = async () => {
    setSubmitting(true);
    setError(null);
    try {
      await charge(amount);
      navigate(-1);
    } catch (e) {
      // 충전에 실패하면 화면에 머물러 사유를 보여준다.
      setError(isApiError(e) ? e.message : "충전에 실패했어요.");
      setSubmitting(false);
    }
  };

  return (
    <>
      <Card variant="accent" className="flex flex-col items-center gap-0.5">
        <span className="text-2xs text-muted">현재 보유</span>
        <span className="text-2xl font-bold text-teal-700">
          {currentPoints.toLocaleString()} P
        </span>
      </Card>

      <div className="flex flex-col gap-2">
        <div className="relative">
          <TextField
            label="금액"
            inputMode="numeric"
            value={amount}
            onChange={(e) => setAmount(Number(e.target.value) || 0)}
          />
          <span className="absolute right-3.5 bottom-3 text-md text-muted">
            원
          </span>
        </div>

        <div className="flex gap-2">
          {QUICK_AMOUNTS.map((value) => (
            <Button key={value} size="sm" block onClick={() => add(value)}>
              +{value}원
            </Button>
          ))}
        </div>

        <p className="text-2xs text-muted">
          {MIN_AMOUNT.toLocaleString()}원 ~ {MAX_AMOUNT.toLocaleString()}원까지
          충전할 수 있어요.
        </p>
      </div>

      <div className="flex flex-col gap-2">
        <p className="text-sm font-semibold text-navy-900">결제 수단</p>
        <RadioOption label="신용카드" selected onSelect={() => {}} />
      </div>

      <Card variant="hero" className="flex flex-col gap-1.5">
        <div className="flex items-center justify-between text-2xs">
          <span className="opacity-70">결제 금액</span>
          <span className="font-bold">{formatted} 원</span>
        </div>
        <div className="flex items-center justify-between text-2xs">
          <span className="opacity-70">충전 포인트</span>
          <span className="font-bold">+{formatted} P</span>
        </div>
      </Card>

      {error && <p className="text-sm text-status-danger">{error}</p>}

      <Button
        variant="navy"
        block
        disabled={outOfRange || submitting}
        onClick={onCharge}
      >
        {submitting ? "결제 중…" : `${formatted}원 결제하고 충전`}
      </Button>
    </>
  );
}

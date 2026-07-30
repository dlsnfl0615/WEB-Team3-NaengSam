import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { Button, Card } from "@/shared/ui";
import { useWalletStore } from "@/shared/store/walletStore";

/** 전환 금액(1:1). */
const CONVERT_AMOUNT = 10000;

/** 머니를 포인트로 전환하는 본문. */
export function ConvertForm() {
  const navigate = useNavigate();
  const money = useWalletStore((s) => s.money);
  const points = useWalletStore((s) => s.points);
  const convert = useWalletStore((s) => s.convert);
  const [submitting, setSubmitting] = useState(false);

  const amount = Math.min(CONVERT_AMOUNT, money);

  const onConvert = async () => {
    setSubmitting(true);
    try {
      await convert(amount);
      navigate(-1);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <>
      <h1 className="text-xl font-bold tracking-[-0.4px] text-navy-900">
        머니를 포인트로
        <br />
        전환할까요?
      </h1>
      <span className="h-[9px] w-3/4 rounded-[5px] bg-track" />

      <Card className="flex flex-col gap-3">
        <div className="flex items-center justify-between">
          <span className="text-sm text-muted">보유 머니</span>
          <span className="text-md font-bold text-navy-900">
            ₩{money.toLocaleString()}
          </span>
        </div>

        <div className="flex flex-col items-center gap-0.5 rounded-md bg-track py-4">
          <span className="text-2xs text-muted">전환 전 포인트</span>
          <span className="text-xl font-bold text-navy-900">
            {points.toLocaleString()}P
          </span>
        </div>

        <div className="flex flex-col items-center gap-0.5 rounded-md bg-teal-50 py-4">
          <span className="text-2xs text-muted">전환 후 포인트</span>
          <span className="text-xl font-bold text-teal-700">
            +{amount.toLocaleString()} P
          </span>
        </div>
      </Card>

      <Card className="flex flex-col items-center gap-0.5">
        <span className="text-2xs text-muted">전환 비율</span>
        <span className="text-base font-bold text-navy-900">
          1 : 1 (수수료 무료)
        </span>
      </Card>

      <p className="text-2xs text-muted">
        ※ 포인트는 배송 결제에만 사용 가능하며 머니로 되돌릴 수 없어요.
      </p>

      <Button
        variant="navy"
        block
        disabled={amount === 0 || submitting}
        onClick={onConvert}
      >
        {submitting
          ? "전환 중…"
          : `${amount.toLocaleString()}P로 전환하기`}
      </Button>
    </>
  );
}

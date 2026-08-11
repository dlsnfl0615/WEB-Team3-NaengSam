import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { Button, Card, TextField } from "@/shared/ui";
import { isApiError } from "@/shared/api";
import { useWalletStore } from "@/shared/store/walletStore";

/** 백엔드 ExchangeRequest 제약(@Min)과 맞춘 최소 전환 금액. */
const MIN_AMOUNT = 1000;

/** 머니를 포인트로 전환하는 본문. */
export function ConvertForm() {
  const navigate = useNavigate();
  const money = useWalletStore((s) => s.money);
  const points = useWalletStore((s) => s.points);
  const exchange = useWalletStore((s) => s.exchange);
  const load = useWalletStore((s) => s.load);
  const [amount, setAmount] = useState(MIN_AMOUNT);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // 보유 머니를 알아야 상한을 걸 수 있으므로 지갑 화면을 거치지 않고 들어와도 한 번 조회한다.
  useEffect(() => {
    load();
  }, [load]);

  const invalid = amount < MIN_AMOUNT || amount > money;

  const onConvert = async () => {
    setSubmitting(true);
    setError(null);
    try {
      await exchange(amount);
      navigate(-1);
    } catch (e) {
      // 전환에 실패하면 화면에 머물러 사유를 보여준다(잔액 부족 등).
      setError(isApiError(e) ? e.message : "전환에 실패했어요.");
      setSubmitting(false);
    }
  };

  return (
    <>
      <h1 className="text-xl font-bold tracking-[-0.4px] text-navy-900">
        머니를 포인트로 전환할까요?
      </h1>
      {/*<span className="h-[9px] w-3/4 rounded-[5px] bg-track" />*/}

      <Card className="flex flex-col gap-3">
        <div className="flex items-center justify-between">
          <span className="text-sm text-muted">보유 머니</span>
          <span className="text-md font-bold text-navy-900">
            ₩{money.toLocaleString()}
          </span>
        </div>

        <div className="relative">
          <TextField
            label="전환 금액"
            inputMode="numeric"
            value={amount}
            onChange={(e) => setAmount(Number(e.target.value) || 0)}
          />
          <span className="absolute right-3.5 bottom-3 text-md text-muted">
            원
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
            {(points + amount).toLocaleString()} P
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
        ※ 포인트는 배송 결제에만 사용 가능하며 머니로 되돌릴 수 없어요. 최소{" "}
        {MIN_AMOUNT.toLocaleString()}원부터 보유 머니까지 전환할 수 있어요.
      </p>

      {error && <p className="text-sm text-status-danger">{error}</p>}

      <Button
        variant="navy"
        block
        disabled={invalid || submitting}
        onClick={onConvert}
      >
        {submitting ? "전환 중…" : `${amount.toLocaleString()}P로 전환하기`}
      </Button>
    </>
  );
}

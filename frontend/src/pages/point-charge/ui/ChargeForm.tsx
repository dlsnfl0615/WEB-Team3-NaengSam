import { useState } from "react";
import { Button, Card, RadioOption, TextField } from "@/shared/ui";

const QUICK_AMOUNTS = [1000, 5000, 10000];

/** 카드 결제로 포인트를 충전하는 본문. */
export function ChargeForm() {
  const [amount, setAmount] = useState(5000);

  const add = (value: number) => setAmount((prev) => prev + value);
  const formatted = amount.toLocaleString();

  return (
    <>
      <Card variant="accent" className="flex flex-col items-center gap-0.5">
        <span className="text-2xs text-muted">현재 보유</span>
        <span className="text-2xl font-bold text-teal-700">12,400 P</span>
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

      <Button variant="navy" block disabled={amount === 0}>
        {formatted}원 결제하고 충전
      </Button>
    </>
  );
}

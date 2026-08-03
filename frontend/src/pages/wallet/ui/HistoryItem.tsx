import { Card, IconChip } from "@/shared/ui";
import { cn } from "@/shared/lib/cn";
import type { WalletTransaction } from "@/shared/mock/types";

export interface HistoryItemProps {
  history: WalletTransaction;
}

/** 지갑 거래 내역 아이템. 부호·단위에 맞춰 금액을 표기합니다. */
export function HistoryItem({ history }: HistoryItemProps) {
  const amountText =
    history.unit === "₩"
      ? `${history.incoming ? "+" : "-"}₩${Math.abs(history.amount).toLocaleString()}`
      : `${history.amount > 0 ? "+" : ""}${history.amount.toLocaleString()} P`;

  return (
    <Card className="flex items-center gap-3">
      <IconChip name={history.icon} />
      <div className="min-w-0 flex-1">
        <p className="truncate text-base font-bold text-navy-900">
          {history.title}
        </p>
        <p className="text-2xs text-muted">{history.detail}</p>
      </div>
      <span
        className={cn(
          "text-md font-bold",
          history.incoming ? "text-teal-700" : "text-navy-900",
        )}
      >
        {amountText}
      </span>
    </Card>
  );
}

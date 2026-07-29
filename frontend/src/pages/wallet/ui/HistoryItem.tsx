import { Card, IconChip } from "@/shared/ui";
import { cn } from "@/shared/lib/cn";
import type { WalletHistory } from "./history";

export interface HistoryItemProps {
  history: WalletHistory;
}

/** 지갑 최근 내역 아이템. */
export function HistoryItem({ history }: HistoryItemProps) {
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
        {history.amount}
      </span>
    </Card>
  );
}

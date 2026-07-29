import type { IconName } from "@/shared/ui";

export interface WalletHistory {
  id: string;
  icon: IconName;
  title: string;
  detail: string;
  amount: string;
  /** 잔액이 늘어난 내역이면 티일로 표시합니다. */
  incoming: boolean;
}

/** 지갑 최근 내역. */
export const WALLET_HISTORY: WalletHistory[] = [
  {
    id: "w1",
    icon: "point",
    title: "포인트 충전",
    detail: "7/22 · 카드결제",
    amount: "+10,000 P",
    incoming: true,
  },
  {
    id: "w2",
    icon: "document",
    title: "서류 배송 결제",
    detail: "7/21",
    amount: "-2,000 P",
    incoming: false,
  },
  {
    id: "w3",
    icon: "transfer",
    title: "머니 → 포인트 전환",
    detail: "7/20",
    amount: "+5,000 P",
    incoming: true,
  },
];

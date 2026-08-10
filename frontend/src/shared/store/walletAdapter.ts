import type { IconName } from "@/shared/ui";
import {
  WalletTransactionDtoWalletType,
  type WalletTransactionDto,
} from "@/shared/api";

/** 지갑 거래 1건(화면 모델). WalletTransactionDto에서 파생. */
export interface WalletTransaction {
  id: string;
  icon: IconName;
  title: string;
  detail: string;
  /** 부호 있는 금액(포인트/머니). */
  amount: number;
  /** 단위: 포인트("P") 또는 머니("₩"). */
  unit: "P" | "₩";
  /** 잔액이 늘어난 내역이면 true. */
  incoming: boolean;
}

/** 거래 유형 코드 → 화면 제목. 포인트(POINT_TX)와 머니(MONEY_TX) 유형을 함께 담는다. */
const TX_TITLES: Record<string, string> = {
  CHARGE: "포인트 충전",
  PAYMENT: "배송 결제",
  REFUND: "배송 환불",
  EXCHANGE_IN: "머니 → 포인트 전환",
  EXCHANGE_OUT: "포인트로 전환",
  SETTLEMENT: "배달 수익",
  REVERSAL: "정산 취소",
  CLAIM_ADJUSTMENT: "보상 조정",
};

/** 거래 유형 코드 → 아이콘 이름. */
const TX_ICONS: Record<string, IconName> = {
  CHARGE: "point",
  PAYMENT: "document",
  REFUND: "document",
  EXCHANGE_IN: "transfer",
  EXCHANGE_OUT: "transfer",
  SETTLEMENT: "bank",
  REVERSAL: "bank",
  CLAIM_ADJUSTMENT: "bank",
};

/** ISO 일시 → 내역 표시용 라벨(예: "8/4"). 값이 없으면 빈 문자열. */
function formatTransactionDate(dtm?: string): string {
  if (!dtm) return "";
  const date = new Date(dtm);
  if (Number.isNaN(date.getTime())) return dtm;
  return `${date.getMonth() + 1}/${date.getDate()}`;
}

/**
 * WalletTransactionDto → 화면 모델.
 *
 * 목록 key로 쓸 식별자가 응답에 없어 (인덱스 + 지갑 유형)으로 만든다. 한 번에 20건을 통째로
 * 교체하는 목록이라 항목이 재정렬되지 않는다.
 */
export function toWalletTransaction(
  dto: WalletTransactionDto,
  index: number,
): WalletTransaction {
  const txType = dto.txType ?? "";
  const amount = dto.amount ?? 0;
  return {
    id: `${dto.walletType ?? "POINT"}-${index}`,
    icon: TX_ICONS[txType] ?? "more",
    title: TX_TITLES[txType] ?? "거래",
    detail: formatTransactionDate(dto.createdDtm),
    amount,
    unit: dto.walletType === WalletTransactionDtoWalletType.MONEY ? "₩" : "P",
    incoming: amount > 0,
  };
}

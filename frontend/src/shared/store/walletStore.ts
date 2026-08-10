import { create } from "zustand";
import {
  api,
  isApiError,
  PointChargeRequestPaymentCd,
  type WalletDto,
} from "@/shared/api";
import { toWalletTransaction, type WalletTransaction } from "./walletAdapter";

interface WalletState {
  points: number;
  money: number;
  transactions: WalletTransaction[];
  loading: boolean;
  error: string | null;
  /** 잔액·최근 내역 조회. */
  load: () => Promise<void>;
  /** 카드 결제로 포인트 충전. */
  charge: (amount: number) => Promise<void>;
  /** 머니 → 포인트 전환(1:1). */
  exchange: (amount: number) => Promise<void>;
}

/**
 * 지갑 잔액/내역 전역 스토어. 잔액의 진실은 서버가 가지므로 충전·전환도 낙관적 갱신 없이
 * 응답으로 돌아온 지갑 한 벌로 상태를 통째로 교체한다.
 */
export const useWalletStore = create<WalletState>((set) => {
  const applyWallet = (wallet?: WalletDto) =>
    set({
      points: wallet?.pointAmount ?? 0,
      money: wallet?.moneyAmount ?? 0,
      transactions: (wallet?.recentTransactions ?? []).map(toWalletTransaction),
      loading: false,
      error: null,
    });

  return {
    points: 0,
    money: 0,
    transactions: [],
    loading: false,
    error: null,

    load: async () => {
      set({ loading: true, error: null });
      try {
        const { result } = await api.getWallet();
        applyWallet(result);
      } catch (e) {
        set({
          loading: false,
          error: isApiError(e) ? e.message : "지갑을 불러오지 못했어요.",
        });
      }
    },

    charge: async (amount) => {
      const { result } = await api.chargePoint({
        amount,
        paymentCd: PointChargeRequestPaymentCd.CARD,
      });
      applyWallet(result);
    },

    exchange: async (amount) => {
      const { result } = await api.exchangeMoneyToPoint({ amount });
      applyWallet(result);
    },
  };
});

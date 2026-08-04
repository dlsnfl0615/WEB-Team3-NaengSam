import { create } from "zustand";
import { nextId } from "@/shared/mock/client";
import { charge as chargeApi, convert as convertApi } from "@/shared/mock/walletService";
import { SEED_WALLET } from "@/shared/mock/seed";
import type { Delivery, WalletTransaction } from "@/shared/mock/types";

interface WalletState {
  points: number;
  money: number;
  transactions: WalletTransaction[];
  /** 카드 결제로 포인트 충전. */
  charge: (amount: number) => Promise<void>;
  /** 머니 → 포인트 전환(1:1). */
  convert: (amount: number) => Promise<void>;
  /** 배달 완료 정산 반영(드리미=머니 수익, 부르미=포인트 결제). */
  settleDelivery: (delivery: Delivery) => void;
}

const NOW_LABEL = "방금";

/** 지갑 잔액/내역을 화면 간 공유하는 전역 스토어. */
export const useWalletStore = create<WalletState>((set) => ({
  points: SEED_WALLET.points,
  money: SEED_WALLET.money,
  transactions: SEED_WALLET.transactions,
  charge: async (amount) => {
    await chargeApi({ amount });
    set((s) => ({
      points: s.points + amount,
      transactions: [
        {
          id: nextId("w"),
          icon: "point",
          title: "포인트 충전",
          detail: `${NOW_LABEL} · 카드결제`,
          amount,
          unit: "P",
          incoming: true,
        },
        ...s.transactions,
      ],
    }));
  },
  convert: async (amount) => {
    await convertApi({ amount });
    set((s) => ({
      money: Math.max(0, s.money - amount),
      points: s.points + amount,
      transactions: [
        {
          id: nextId("w"),
          icon: "transfer",
          title: "머니 → 포인트 전환",
          detail: NOW_LABEL,
          amount,
          unit: "P",
          incoming: true,
        },
        ...s.transactions,
      ],
    }));
  },
  settleDelivery: (delivery) => {
    set((s) => {
      if (delivery.myRole === "드리미") {
        return {
          money: s.money + delivery.price,
          transactions: [
            {
              id: nextId("w"),
              icon: delivery.icon,
              title: `${delivery.title} 수익`,
              detail: NOW_LABEL,
              amount: delivery.price,
              unit: "₩",
              incoming: true,
            },
            ...s.transactions,
          ],
        };
      }
      return {
        points: Math.max(0, s.points - delivery.price),
        transactions: [
          {
            id: nextId("w"),
            icon: delivery.icon,
            title: `${delivery.title} 결제`,
            detail: NOW_LABEL,
            amount: -delivery.price,
            unit: "P",
            incoming: false,
          },
          ...s.transactions,
        ],
      };
    });
  },
}));

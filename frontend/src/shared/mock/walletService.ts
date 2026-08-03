import { mockRequest } from "./client";
import { SEED_WALLET } from "./seed";
import type { ChargeRequest, ConvertRequest, Wallet } from "./types";

/**
 * 지갑 목 서비스. 잔액/내역은 walletStore가 소유하고, 이 서비스는
 * 네트워크 왕복만 시뮬레이션한다. 실제 API 연동 시 구현만 교체.
 */

/** 지갑 초기 조회. */
export function getWallet(): Promise<Wallet> {
  return mockRequest(SEED_WALLET);
}

/** 포인트 충전(카드 결제). */
export function charge(dto: ChargeRequest): Promise<void> {
  return mockRequest<void>(undefined, {
    errorMessage: `${dto.amount.toLocaleString()}원 충전에 실패했어요.`,
  });
}

/** 머니 → 포인트 전환. */
export function convert(dto: ConvertRequest): Promise<void> {
  return mockRequest<void>(undefined, {
    errorMessage: `${dto.amount.toLocaleString()} 전환에 실패했어요.`,
  });
}

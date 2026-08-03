import { mockRequest } from "./client";

/**
 * 배달 진행 목 서비스(#48). 상태 전이는 deliveryStore가 소유하고,
 * 이 서비스는 네트워크 왕복만 시뮬레이션한다. 실제 API 연동 시 구현만 교체.
 */

/** 픽업중 → 배송중. */
export function advanceDelivery(id: string): Promise<void> {
  return mockRequest<void>(undefined, {
    delayMs: 300,
    errorMessage: `배달 ${id} 진행에 실패했어요.`,
  });
}

/** → 완료. */
export function completeDelivery(id: string): Promise<void> {
  return mockRequest<void>(undefined, {
    delayMs: 300,
    errorMessage: `배달 ${id} 완료 처리에 실패했어요.`,
  });
}

/** 취소/사고. */
export function cancelDelivery(id: string, reason: string): Promise<void> {
  return mockRequest<void>(undefined, {
    delayMs: 300,
    errorMessage: `배달 ${id} 취소에 실패했어요(${reason}).`,
  });
}

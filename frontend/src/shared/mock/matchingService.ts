import { mockRequest, nextId } from "./client";
import { SEED_CALLS } from "./seed";
import type { Call, Delivery } from "./types";

/**
 * 매칭/콜 목 서비스(#43). 실제 API 연동 시 구현만 교체.
 */

/** 드리미에게 노출되는 대기 콜 목록. */
export function getCalls(): Promise<Call[]> {
  return mockRequest(SEED_CALLS);
}

/** 콜 수락 → 드리미 관점 "픽업중" 배달 생성. */
export function acceptCall(call: Call): Promise<Delivery> {
  const id = nextId("d");
  const [from, to] = call.route.split(" → ");
  const delivery: Delivery = {
    id,
    code: call.code,
    icon: call.icon,
    title: `${call.itemType} 배송 ${call.code}`,
    itemType: call.itemType,
    itemSize: "S",
    pickup: from ? `${call.place} ${from}` : call.place,
    dropoff: to ? `${call.place} ${to}` : call.route,
    price: call.price,
    status: "픽업중",
    myRole: "드리미",
    senderName: "부르미",
    time: "방금",
    note: "픽업 이동 중",
    eta: "3분",
    distance: call.pickupDistance,
  };
  return mockRequest(delivery);
}

import type { IconName } from "@/shared/ui";
import { mockRequest, nextId } from "./client";
import type { CreateDeliveryRequest, Delivery } from "./types";

/**
 * 부름 등록 목 서비스(#43 매칭 전 단계). 실제 API 연동 시 구현만 교체.
 */

const ITEM_ICON: Record<string, IconName> = {
  서류: "document",
  소형택배: "package",
  샘플: "package",
  음료: "drink",
  기타: "package",
};

/** 부름 등록 → "매칭중" 상태의 배달을 생성. */
export function createDelivery(dto: CreateDeliveryRequest): Promise<Delivery> {
  const id = nextId("d");
  const delivery: Delivery = {
    id,
    code: `#B-${id.slice(-3)}`,
    icon: ITEM_ICON[dto.itemType] ?? "package",
    title: `${dto.itemType} 배송`,
    itemType: dto.itemType,
    itemSize: dto.itemSize,
    itemName: dto.itemName,
    pickup: dto.pickup,
    dropoff: dto.dropoff,
    price: dto.price,
    status: "매칭중",
    myRole: "부르미",
    time: "방금",
    note: "매칭 대기 중",
  };
  return mockRequest(delivery);
}

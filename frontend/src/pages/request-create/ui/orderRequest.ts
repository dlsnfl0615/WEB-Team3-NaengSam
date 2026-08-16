import {
  OrderRequestItemCd,
  OrderRequestItemSizeCd,
  type OrderRequest,
} from "@/shared/api";
import type { RequestForm } from "./types";

/** 폼의 한글 물품 유형 → 백엔드 물건 유형 코드. */
export function itemTypeToCd(
  itemType: RequestForm["itemType"],
): OrderRequestItemCd {
  switch (itemType) {
    case "서류":
      return OrderRequestItemCd.DOCUMENT;
    case "소형택배":
      return OrderRequestItemCd.PACKAGE;
    case "샘플":
      return OrderRequestItemCd.SAMPLE;
    default:
      return OrderRequestItemCd.ETC;
  }
}

/** 폼의 물품 크기 → 백엔드 물건 크기 코드. */
export function itemSizeToCd(
  itemSize: RequestForm["itemSize"],
): OrderRequestItemSizeCd {
  return itemSize === "M" ? OrderRequestItemSizeCd.M : OrderRequestItemSizeCd.S;
}

/**
 * 요청사항 태그 → 배송 요청 문구.
 * 직접 입력(etc)은 "기타"에서만 쓰이므로, 그때는 "기타 · " 접두사 없이 입력값 그대로 보낸다.
 * (접두사를 붙이면 최대 261자가 되어 백엔드 OrderRequest의 @Size(max = 255)에 걸린다.)
 */
function toDeliveryRequest(form: RequestForm): string | undefined {
  if (form.requestTag === "기타") return form.etc.trim() || "기타";
  return form.requestTag !== "없음" ? form.requestTag : undefined;
}

/** 부름 등록 폼 → subscribeOrder 요청 바디. */
export function toOrderRequest(form: RequestForm): OrderRequest {
  return {
    originAddressLine1: form.pickup.trim(),
    originAddressLine2: form.pickupDetail.trim() || undefined,
    destinationAddressLine1: form.dropoff.trim(),
    destinationAddressLine2: form.dropoffDetail.trim() || undefined,
    itemName: form.itemName.trim(),
    itemCd: itemTypeToCd(form.itemType),
    itemSizeCd: itemSizeToCd(form.itemSize),
    imageKey: form.imageKey || undefined,
    itemDetail: form.detail.trim() || undefined,
    deliveryRequest: toDeliveryRequest(form),
  };
}

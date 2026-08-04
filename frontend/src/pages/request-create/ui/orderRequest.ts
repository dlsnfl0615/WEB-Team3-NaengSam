import { OrderRequestItemCd, type OrderRequest } from "@/shared/api";
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

/** 요청사항 태그 + 직접 입력을 한 문장으로 결합(빈 값은 제외). */
function toDeliveryRequest(form: RequestForm): string | undefined {
  const parts = [
    form.requestTag !== "없음" ? form.requestTag : "",
    form.etc.trim(),
  ].filter(Boolean);
  return parts.length ? parts.join(" · ") : undefined;
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
    imageKey: form.imageKey || undefined,
    itemDetail: form.detail.trim() || undefined,
    deliveryRequest: toDeliveryRequest(form),
  };
}

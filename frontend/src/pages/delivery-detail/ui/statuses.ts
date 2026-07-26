/** 드림 상세 화면이 다루는 배달 상태(쿼리스트링 ?status= 로 지정). */
export type DetailStatus = "픽업중" | "배송중" | "지연";

export const DETAIL_STATUSES: Record<
  DetailStatus,
  { title: string; showEta: boolean; cancelable: boolean }
> = {
  픽업중: {
    title: "물품을 픽업 중이에요",
    showEta: true,
    cancelable: true,
  },
  배송중: {
    title: "물품을 드림 중이에요",
    showEta: true,
    cancelable: false,
  },
  지연: {
    title: "물품배송이 지연되고 있어요",
    showEta: false,
    cancelable: false,
  },
};

import { DeliveryStatusResponseDtoStatus } from "@/shared/api";

/**
 * 주문별 마지막으로 관측한 배달 상태 스냅샷(sessionStorage).
 *
 * 배달 현황 조회 GET 엔드포인트가 없어, 추적 화면을 다시 열면 실제로는 배송중이어도
 * 픽업중으로 되돌아간다. 그 사이를 메우는 스톱갭이다 — 서버 조회가 생기면 이 모듈을 걷어낸다.
 * 탭/브라우저를 닫으면 사라지므로 복원 실패는 정상 경로로 취급한다(기본값으로 시작).
 */
const STORAGE_KEY = "naengsam.deliveryStage";

type StageMap = Record<string, DeliveryStatusResponseDtoStatus>;

function readMap(): StageMap {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY);
    return raw ? (JSON.parse(raw) as StageMap) : {};
  } catch {
    return {};
  }
}

/** 관측한 배달 상태를 기록한다. */
export function rememberDeliveryStage(
  orderId: string,
  status: DeliveryStatusResponseDtoStatus,
): void {
  const map = readMap();
  map[orderId] = status;
  try {
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(map));
  } catch {
    // 저장 실패(용량·프라이빗 모드)는 복원을 포기할 뿐 화면 동작에 영향이 없다.
  }
}

/** 기록된 배달 상태를 돌려준다(없으면 undefined). */
export function recallDeliveryStage(
  orderId: string,
): DeliveryStatusResponseDtoStatus | undefined {
  const status = readMap()[orderId];
  return status && status in DeliveryStatusResponseDtoStatus
    ? status
    : undefined;
}

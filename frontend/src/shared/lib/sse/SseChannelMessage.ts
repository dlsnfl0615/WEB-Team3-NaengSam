/** {@link SseTabCoordinator}가 관측하는 서버 SSE 연결 상태. 백엔드 SseStatus와 이름을 맞춘다. */
export type SseTabStatus = "connecting" | "connected" | "reconnecting" | "closed";

/**
 * 대표 탭이 Web Lock을 획득했음을 알린다. 다른 탭은 이 메시지를 받으면 자신의 구독 목록 스냅샷을
 * 다시 보낸다(대표 탭이 바뀌면 새 대표는 이전 원격 구독 상태를 모르므로).
 *
 * `epoch`는 대표 탭이 바뀔 때마다 오르는 값이다. 새 `leader-ready`보다 낮은 epoch를 가진 상태/이벤트
 * 메시지가 뒤늦게 도착해도(네트워크 재정렬 등) 무시할 수 있게 해, 죽은 이전 대표의 메시지가 현재 대표
 * 상태를 덮어쓰지 못하게 막는다.
 */
export interface LeaderReadyMessage {
  type: "leader-ready";
  senderId: string;
  epoch: number;
}

/** 탭 하나의 전체 구독 이벤트 이름 스냅샷. local subscribe/unsubscribe가 바뀔 때마다 통째로 다시 보낸다. */
export interface SubscriptionsMessage {
  type: "subscriptions";
  peerId: string;
  eventNames: string[];
}

/** 대표 탭이 서버로부터 받은 이벤트를 다른 탭에 그대로 전달한다. */
export interface SseEventMessage {
  type: "event";
  senderId: string;
  eventName: string;
  payload: unknown;
}

/** 대표 탭의 연결 상태 변화를 다른 탭에 전달한다. */
export interface SseStatusMessage {
  type: "status";
  senderId: string;
  status: SseTabStatus;
}

/** follower 탭에서 사용자가 수동 재연결을 요청하면 대표 탭에 전달한다. */
export interface ReconnectRequestMessage {
  type: "reconnect-request";
  senderId: string;
}

/**
 * 정상 종료하는 탭이 자신의 원격 구독 목록 제거를 대표 탭에 요청한다. 전달 보장이 없으므로(탭이 죽거나
 * 네트워크가 끊기면 유실될 수 있다) correctness를 이 메시지에 의존하지 않는다 — stale 구독이 잠시 남아
 * 불필요한 이벤트 이름을 더 구독하게 되는 정도는 허용한다. 대표 탭이 바뀌면 원격 구독 맵 자체를 비우고
 * 살아 있는 탭들이 `leader-ready`에 반응해 다시 스냅샷을 보내므로 결국 정리된다.
 */
export interface PeerClosingMessage {
  type: "peer-closing";
  peerId: string;
}

export type SseChannelMessage =
  | LeaderReadyMessage
  | SubscriptionsMessage
  | SseEventMessage
  | SseStatusMessage
  | ReconnectRequestMessage
  | PeerClosingMessage;

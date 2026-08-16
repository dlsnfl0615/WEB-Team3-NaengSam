import { create } from "zustand";
import {
  api,
  isApiError,
  type CurrentMatchingStatusDto,
  type DreamiProfileDto,
  type NearbyCallDto,
  type PendingOfferDto,
} from "@/shared/api";
import type { Coords } from "@/shared/ui";

/** 주변 콜 조회 반경(m)·최대 건수(백엔드 상한 10). */
const NEARBY_RADIUS_M = 3000;
const NEARBY_COUNT = 10;
/** 위치 취득 제한시간(ms). 넘기면 실패로 보고 사용자에게 재시도를 시킨다. */
const GEOLOCATION_TIMEOUT_MS = 10_000;
/** SSE 장애 중 매칭 상태를 되찾기 위한 polling 주기(ms). */
export const MATCHING_POLL_INTERVAL_MS = 3_000;

type NearbyCallsLoadResult = "loaded" | "location-unavailable" | "failed";

/** 위치 권한 거부·브라우저 미지원처럼 드리미 화면을 사용할 수 없는 오류. */
class LocationAccessError extends Error {}

/**
 * 백엔드 SSE `offer_popup` payload. 물품 사진은 여기 안 실려온다 — 콜 카드에서 물품사진 버튼을
 * 누른 시점에 `api.getOfferItemPhoto(offerId)`로 그때 조회한다(자세한 이유는 백엔드 OfferPopupPayload 참고).
 */
interface OfferPopupPayload {
  offerId: string;
  orderId: string;
  deliveryAmount: number | null;
  itemName: string | null;
  deliveryEta: number;
  deliveryDistance: number | null;
  originLatitude: number | null;
  originLongitude: number | null;
  originAlias: string | null;
  originAddressLine1: string | null;
  destinationLatitude: number | null;
  destinationLongitude: number | null;
  destinationAlias: string | null;
  destinationAddressLine1: string | null;
  /** 부르미가 작성한 요청 사항. 없으면 null. */
  deliveryRequest: string | null;
  /** 제안이 생성된 시각. 응답 마감(expiresAt)과 함께 카운트다운 계산에 쓴다. */
  offeredAt: string;
  /** 응답 마감 절대 시각. */
  expiresAt: string;
}

/** 드리미가 받은 제안. 픽업 거리만 주변 콜 캐시에서 보충한다. */
export interface PendingOffer extends OfferPopupPayload {
  distanceMeters?: number;
}

/**
 * 드리미가 콜을 수락하고 부르미의 확정을 기다리는 상태.
 *
 * 백엔드는 이 대기 자체를 알려주는 이벤트나 스냅샷 필드를 두지 않는다(수락 응답이 곧 시작 신호다).
 * 그래서 수락이 성공한 순간 프론트에서 만들고, 결말을 알려주는 기존 SSE 이벤트
 * (`boormi_rejected`·`offer_closed`·`delivery_started_dreami`)에서 지운다. 이벤트를 하나도 못 받는
 * 최악의 경우에도 화면에 남지 않도록 부르미 확인 TTL(`BOORMI_CONFIRM_TTL_MS`)로 스스로 만료된다.
 */
export interface AwaitingBoormi {
  offerId: string;
  orderId: string;
  /** 수락한 콜의 물품명(카드에 무엇을 기다리는지 보여주기 위함). */
  itemName: string | null;
  /** 대기 시작 시각(ISO). 카운트다운 기준. */
  acceptedAt: string;
  /** 부르미 확인 마감 시각(ISO). */
  expiresAt: string;
}

/**
 * 부르미 확인 대기 TTL. 백엔드 `MatchingService.BOORMI_OFFER_TTL`(30초)과 같은 값이며,
 * 대기 카드가 영원히 남지 않게 하는 상한으로만 쓴다(판정은 언제나 서버 이벤트가 한다).
 */
const BOORMI_CONFIRM_TTL_MS = 30_000;

/** 부르미가 받은 드리미 수락 알림(SSE `dreami_info`) + 드리미 프로필. */
export interface IncomingDreami {
  offerId: string;
  orderId: string;
  dreamiId: string;
  /** 드리미 픽업 예상 소요 시간(분). 직선거리 기반 추정이며 위치를 모르면 null. */
  pickupEtaMinutes?: number | null;
  /** 드리미가 수락한 시각. 응답 마감(expiresAt)과 함께 카운트다운 계산에 쓴다. */
  acceptedAt?: string;
  /** 부르미 확인 응답 마감 절대 시각. */
  expiresAt?: string;
  profile?: DreamiProfileDto;
}

interface MatchingState {
  /** 드리미 온라인(콜 수신 가능) 상태. */
  online: boolean;
  /** 드리미 진입 시 구한 좌표(주변 콜 조회·온라인 전환에 재사용 — GPS 중복 조회 방지). */
  myLocation: Coords | null;
  /** 주변 콜 목록. 제안 팝업의 픽업 거리 보조 데이터로도 쓴다. */
  nearbyCalls: NearbyCallDto[];
  /** 주변 콜 조회 실패 사유(온라인 전환 메시지와는 별개 관심사). */
  nearbyCallsError: string | null;
  pendingOffer: PendingOffer | null;
  incomingDreami: IncomingDreami | null;
  /** 드리미가 수락하고 부르미 확정을 기다리는 중(드리미 화면 전용). */
  awaitingBoormi: AwaitingBoormi | null;
  /** 마지막 오류/안내 메시지(팝업·화면에서 노출 후 clearMessage). */
  message: string | null;
  /** 수락·거절 요청 진행 중(버튼 중복 클릭 방지). */
  submitting: boolean;

  /** SSE `offer_popup` 수신(드리미). */
  receiveOfferPopup: (payload: unknown) => void;
  /** SSE `offer_closed` 수신(드리미). */
  receiveOfferClosed: (payload: unknown) => void;
  /** SSE `boormi_rejected` 수신(드리미). */
  receiveBoormiRejected: (payload: unknown) => void;
  /** SSE `dreami_info` 수신(부르미). */
  receiveDreamiInfo: (payload: unknown) => void;
  /** SSE `offer_error` 수신(공통). */
  receiveOfferError: (payload: unknown) => void;
  /** 화면 이탈·로그아웃 시 진행 중 팝업 정리. */
  clearOffers: () => void;
  /** 드리미 응답 카운트다운 만료(로컬). offerId가 다르면(이미 새 제안으로 교체됐으면) 무시한다. */
  expirePendingOffer: (offerId: string) => void;
  /** 부르미 확인 카운트다운 만료(로컬). offerId가 다르면 무시한다. */
  expireIncomingDreami: (offerId: string) => void;
  /** 드리미의 '부르미 응답 대기' 종료(로컬 TTL 만료). offerId가 다르면 무시한다. */
  expireAwaitingBoormi: (offerId: string) => void;
  /** 배달이 실제로 시작돼(delivery_started_dreami) 대기가 끝났을 때 정리. */
  clearAwaitingBoormi: () => void;
  /** 드리미 화면 진입 시(및 폴링 시) 실행: 좌표를 구해 주변 콜을 조회한다. 온라인 여부와 무관하게 항상 동작한다. */
  loadNearbyCalls: () => Promise<NearbyCallsLoadResult>;
  /** 드리미 온라인 전환. myLocation이 있으면 재사용하고, 없으면 새로 조회한다. */
  goOnline: () => Promise<void>;
  goOffline: () => Promise<void>;
  /** 드리미 제안 수락 → 성공 시 orderId 반환(실패 시 null). */
  acceptOffer: () => Promise<string | null>;
  rejectOffer: () => Promise<void>;
  /** 부르미 최종 확정 → 성공 시 orderId 반환(실패 시 null). */
  confirmDreami: () => Promise<string | null>;
  rejectDreami: () => Promise<void>;
  clearMessage: () => void;
  /**
   * 현재 매칭 상태를 서버에서 즉시 조회해 팝업 상태를 맞춘다. SSE로 놓친 이벤트를 복구하는 용도.
   * 응답의 pendingOffer/incomingDreami가 null이면 남아 있던 팝업을 제거한다.
   */
  syncCurrentMatching: () => Promise<void>;
  /** SSE 장애 중 상태를 되찾기 위한 polling 시작(즉시 1회 동기화 + 주기 반복). 이미 돌고 있으면 무시. */
  startMatchingPolling: () => void;
  /** polling 중단 + timer 제거. */
  stopMatchingPolling: () => void;
}

/** 위치 취득 실패 사유별 안내. 사용자가 다음 행동을 고를 수 있게 구분한다. */
function geolocationMessage(error: GeolocationPositionError): string {
  switch (error.code) {
    case error.PERMISSION_DENIED:
      return "위치 권한이 필요해요. 권한을 허용한 뒤 다시 시도해주세요.";
    case error.TIMEOUT:
      return "위치 확인이 오래 걸려요. 실외로 이동한 뒤 다시 시도해주세요.";
    default:
      return "현재 위치를 확인할 수 없어요. 잠시 후 다시 시도해주세요.";
  }
}

/**
 * 현재 좌표. 권한 거부·미지원이면 reject한다(임의 좌표를 서버에 넣지 않는다).
 * timeout을 주지 않으면 위치 확정이 지연될 때 Promise가 영영 풀리지 않아
 * 온라인 전환 요청 자체가 나가지 않는다.
 */
function getCurrentCoords(): Promise<Coords> {
  return new Promise((resolve, reject) => {
    if (!navigator.geolocation) {
      reject(new LocationAccessError("이 브라우저에서는 위치를 사용할 수 없어요."));
      return;
    }
    navigator.geolocation.getCurrentPosition(
      ({ coords }) =>
        resolve({ latitude: coords.latitude, longitude: coords.longitude }),
      (error) =>
        reject(
          error.code === error.PERMISSION_DENIED
            ? new LocationAccessError(geolocationMessage(error))
            : new Error(geolocationMessage(error)),
        ),
      {
        enableHighAccuracy: true,
        timeout: GEOLOCATION_TIMEOUT_MS,
        maximumAge: 0,
      },
    );
  });
}

function toMessage(e: unknown, fallback: string): string {
  if (isApiError(e)) return e.message;
  return e instanceof Error ? e.message : fallback;
}

/** nullable 숫자를 store 표준형(number | null)으로 정규화한다. */
function num(v: number | undefined): number | null {
  return v ?? null;
}

/** nullable 문자열을 store 표준형(string | null)으로 정규화한다. */
function str(v: string | undefined): string | null {
  return v ?? null;
}

/**
 * 스냅샷의 PendingOfferDto(offerId + OrderSummaryDto + offeredAt/expiresAt) → store의 PendingOffer로 매핑한다.
 * offeredAt/expiresAt은 SSE 팝업과 같은 값이라 폴링으로 복구해도 카운트다운이 정확하다.
 */
function pendingOfferFromSnapshot(
  dto: PendingOfferDto,
  nearbyCalls: NearbyCallDto[],
): PendingOffer {
  const summary = dto.orderSummary ?? {};
  const orderId = summary.orderId ?? "";
  return {
    offerId: dto.offerId ?? "",
    orderId,
    deliveryAmount: num(summary.deliveryAmount),
    itemName: str(summary.itemName),
    deliveryEta: summary.deliveryEta ?? 0,
    deliveryDistance: num(summary.deliveryDistance),
    originLatitude: num(summary.originLatitude),
    originLongitude: num(summary.originLongitude),
    originAlias: str(summary.originAlias),
    originAddressLine1: str(summary.originAddressLine1),
    destinationLatitude: num(summary.destinationLatitude),
    destinationLongitude: num(summary.destinationLongitude),
    destinationAlias: str(summary.destinationAlias),
    destinationAddressLine1: str(summary.destinationAddressLine1),
    deliveryRequest: str(summary.deliveryRequest),
    offeredAt: dto.offeredAt ?? "",
    expiresAt: dto.expiresAt ?? "",
    distanceMeters: nearbyCalls.find((c) => c.orderId === orderId)?.distanceMeters,
  };
}

/**
 * SSE 장애 중 상태를 되찾는 polling 자원. 렌더와 무관하므로 store 상태가 아니라 모듈 스코프로 둔다.
 * `pollTimer`: setInterval 핸들(중복 시작 방지). `syncing`: in-flight 동기화 요청(중복 요청 방지).
 */
let pollTimer: ReturnType<typeof setInterval> | null = null;
let syncing = false;

/**
 * 온라인/오프라인 전환이 서버 응답을 기다리는 중인지. 그 사이 도착한 동기화 응답은 전환 이전 상태를
 * 담고 있어, 방금 누른 시작하기/종료를 되돌려버린다.
 */
let togglingOnline = false;

/**
 * pendingOffer/incomingDreami/awaitingBoormi에 영향을 줄 수 있는 이벤트(offer_popup·offer_closed·
 * offer_error·boormi_rejected·dreami_info·로컬 만료·수락/거절 성공)가 있을 때마다 증가하는 내부 카운터.
 * `syncCurrentMatching`은 요청 시작 시점의 값을 저장해두고, 응답이 도착했을 때 값이 달라졌으면
 * (그 사이 SSE 등으로 이미 상태가 바뀌었으면) 그 snapshot을 적용하지 않고 버린다 — HTTP 응답이 SSE보다
 * 늦게 도착해 최신 상태를 오래된 것으로 덮어쓰는 순서 역전을 막는다.
 */
let matchingRevision = 0;
function bumpMatchingRevision(): void {
  matchingRevision += 1;
}

/**
 * 실 매칭 상태 전역 스토어. 수락·거절·최종 확정을 실제 매칭 API로 처리한다.
 *
 * SSE 연결 자체는 이 스토어가 관리하지 않는다 — `MatchingPopup`이 기존 `useSse` 훅으로
 * 구독하고, 수신 payload를 아래 `receive*` 액션에 넘긴다.
 *
 * 역할별 흐름:
 * - 드리미: `loadNearbyCalls`(진입 시/폴링)·`goOnline`(시작하기) → `offer_popup` 수신 → `acceptOffer`/`rejectOffer`
 * - 부르미: 콜 등록(boormiOrderStore) → `dreami_info` 수신 → `confirmDreami`/`rejectDreami`
 */
export const useMatchingStore = create<MatchingState>((set, get) => ({
  online: false,
  myLocation: null,
  nearbyCalls: [],
  nearbyCallsError: null,
  pendingOffer: null,
  incomingDreami: null,
  awaitingBoormi: null,
  message: null,
  submitting: false,

  // 드리미: 새 제안 도착. 표시 정보는 SSE payload를 쓰고 거리만 주변 콜 캐시에서 보충한다.
  receiveOfferPopup: (payload) => {
    bumpMatchingRevision();
    const offer = payload as OfferPopupPayload;
    set((s) => ({
      pendingOffer: {
        ...offer,
        distanceMeters: s.nearbyCalls.find((c) => c.orderId === offer.orderId)
          ?.distanceMeters,
      },
      // 새 제안이 왔으면 지난 안내는 지운다(카드 뒤에 남아 나중에 다시 뜨는 것 방지).
      message: null,
    }));
  },

  // 드리미: 제안 마감(선착순 마감·거절 처리 완료 등). 이미 수락해 대기 중이던 제안이 마감된 것이면
  // '부르미 응답 대기' 카드도 함께 내린다.
  receiveOfferClosed: (payload) => {
    bumpMatchingRevision();
    const { offerId, reason } = payload as { offerId: string; reason?: string };
    set((s) =>
      s.pendingOffer?.offerId === offerId || s.awaitingBoormi?.offerId === offerId
        ? {
            pendingOffer:
              s.pendingOffer?.offerId === offerId ? null : s.pendingOffer,
            awaitingBoormi:
              s.awaitingBoormi?.offerId === offerId ? null : s.awaitingBoormi,
            message: reason ?? "제안이 마감됐어요.",
          }
        : {},
    );
  },

  // 드리미: 부르미가 거절함. 수락 후 띄워둔 '부르미 응답 대기' 카드를 내리는 지점이기도 하다.
  receiveBoormiRejected: (payload) => {
    bumpMatchingRevision();
    const { offerId } = payload as { offerId: string };
    set((s) => ({
      pendingOffer: s.pendingOffer?.offerId === offerId ? null : s.pendingOffer,
      awaitingBoormi:
        s.awaitingBoormi?.offerId === offerId ? null : s.awaitingBoormi,
      message: "부르미가 요청을 거절했어요.",
    }));
  },

  // 부르미: 드리미가 수락 → 프로필을 붙여 확정 카드를 띄운다.
  receiveDreamiInfo: (payload) => {
    bumpMatchingRevision();
    const { offerId, orderId, dreamiId, pickupEtaMinutes, acceptedAt, expiresAt } = payload as {
      offerId: string;
      orderId: string;
      dreamiId: string;
      pickupEtaMinutes?: number | null;
      acceptedAt?: string;
      expiresAt?: string;
    };
    set({
      incomingDreami: { offerId, orderId, dreamiId, pickupEtaMinutes, acceptedAt, expiresAt },
      message: null,
    });
    api
      .getProfile(dreamiId)
      .then(({ result }) => {
        set((s) =>
          s.incomingDreami?.offerId === offerId
            ? { incomingDreami: { ...s.incomingDreami, profile: result } }
            : {},
        );
      })
      .catch(() => {
        // 프로필 조회 실패는 치명적이지 않다 — 이름 없이 확정 카드를 유지한다.
      });
  },

  // 매칭 처리가 실패했으면 그 제안은 더 진행되지 않는다 → 대기 카드도 남겨두지 않는다.
  // offerId로 대상을 가려서, 이미 새 offer로 교체된 뒤 뒤늦게 도착한 오류가 그 새 offer를
  // 지우거나 사용자에게 새 offer가 실패한 것처럼 보이게 하지 않는다.
  receiveOfferError: (payload) => {
    bumpMatchingRevision();
    const { offerId, message } = payload as { offerId?: string | null; message?: string };
    set((s) => {
      // 등록/주문 단위 오류처럼 특정 제안과 무관하면(offerId 없음) 안내만 띄우고 팝업 상태는 건드리지 않는다.
      if (offerId == null) {
        return { message: message ?? "매칭 요청 처리에 실패했어요." };
      }
      const matchesPending = s.pendingOffer?.offerId === offerId;
      const matchesAwaiting = s.awaitingBoormi?.offerId === offerId;
      // 지금 화면의 pendingOffer/awaitingBoormi 어느 쪽과도 관련 없으면, 이미 지나간 offer의
      // 오래된 오류다 — 조용히 무시한다(상태도, 안내 메시지도 남기지 않는다).
      if (!matchesPending && !matchesAwaiting) {
        return {};
      }
      return {
        pendingOffer: matchesPending ? null : s.pendingOffer,
        awaitingBoormi: matchesAwaiting ? null : s.awaitingBoormi,
        message: message ?? "매칭 요청 처리에 실패했어요.",
      };
    });
  },

  clearOffers: () =>
    set({
      pendingOffer: null,
      incomingDreami: null,
      awaitingBoormi: null,
    }),

  // 드리미 카운트다운 만료(로컬). 그 사이 새 제안으로 교체됐으면(offerId 다름) 건드리지 않는다.
  expirePendingOffer: (offerId) => {
    bumpMatchingRevision();
    set((s) => (s.pendingOffer?.offerId === offerId ? { pendingOffer: null } : {}));
  },

  // 부르미 카운트다운 만료(로컬). 그 사이 다른 확인 대기로 교체됐으면 건드리지 않는다.
  expireIncomingDreami: (offerId) => {
    bumpMatchingRevision();
    set((s) => (s.incomingDreami?.offerId === offerId ? { incomingDreami: null } : {}));
  },

  // 부르미 확인 TTL이 지나도록 아무 이벤트도 못 받은 경우의 안전장치. 결말은 서버가 알고 있으므로
  // 단정적인 문구 대신 대기 종료만 알리고, 다음 오퍼를 받을 수 있는 상태로 되돌린다.
  expireAwaitingBoormi: (offerId) => {
    bumpMatchingRevision();
    set((s) =>
      s.awaitingBoormi?.offerId === offerId
        ? {
            awaitingBoormi: null,
            message: "부르미의 응답을 받지 못했어요. 다음 콜을 기다려주세요.",
          }
        : {},
    );
  },

  clearAwaitingBoormi: () => set({ awaitingBoormi: null }),

  // 온라인 여부와 무관하게 화면 진입 시(및 폴링 시) 항상 실행한다. myLocation이 이미 있으면 재사용한다.
  loadNearbyCalls: async () => {
    let coords = get().myLocation;
    try {
      if (!coords) {
        coords = await getCurrentCoords();
        set({ myLocation: coords });
      }
      const { result } = await api.findNearbyCalls({
        lat: coords.latitude,
        lng: coords.longitude,
        radius: NEARBY_RADIUS_M,
        count: NEARBY_COUNT,
      });
      set({ nearbyCalls: result ?? [], nearbyCallsError: null });
      return "loaded";
    } catch (e) {
      set({ nearbyCallsError: toMessage(e, "주변 콜을 불러오지 못했어요.") });
      return e instanceof LocationAccessError
        ? "location-unavailable"
        : "failed";
    }
  },

  goOnline: async () => {
    // 이미 온라인이면 재등록하지 않는다(서버가 "이미 등록된 드리미입니다"로 응답한다).
    if (get().online) return;

    // loadNearbyCalls가 이미 구해둔 좌표를 재사용한다(위치를 다시 물어 실패하는 경로를 없앤다).
    let coords = get().myLocation;
    togglingOnline = true;
    try {
      if (!coords) {
        coords = await getCurrentCoords();
        set({ myLocation: coords });
      }
      await api.goOnline(coords);
      set({ online: true, message: null });
    } catch (e) {
      set({
        online: false,
        message: toMessage(e, "온라인 전환에 실패했어요."),
      });
    } finally {
      togglingOnline = false;
    }
  },

  goOffline: async () => {
    togglingOnline = true;
    try {
      await api.goOffline();
      bumpMatchingRevision();
      set({ online: false, pendingOffer: null });
    } catch (e) {
      set({ message: toMessage(e, "오프라인 전환에 실패했어요.") });
    } finally {
      togglingOnline = false;
    }
  },

  acceptOffer: async () => {
    const { pendingOffer, submitting } = get();
    if (!pendingOffer || submitting) return null;
    // API 응답을 기다리는 동안 SSE로 새 offer가 도착해 pendingOffer가 교체될 수 있다. 그러니
    // 지금 요청 중인 offer를 미리 캡처해두고, 응답 이후에는 이 값으로만 판단한다.
    const requestedOfferId = pendingOffer.offerId;
    const requestedOrderId = pendingOffer.orderId;
    const requestedItemName = pendingOffer.itemName;
    set({ submitting: true });
    try {
      await api.acceptOffer(requestedOfferId);
      bumpMatchingRevision();
      // 수락은 배달 시작이 아니라 '부르미 확인 대기'의 시작이다. 콜 카드를 내리고 대기 카드로 바꿔,
      // 드리미가 빈 화면에서 무슨 일이 일어나는지 모른 채 기다리지 않게 한다.
      const acceptedAt = Date.now();
      set((current) => {
        // 응답이 오는 사이 이미 다른 offer로 교체됐으면, 그 새 offer를 그대로 두고 submitting만
        // 해제한다 — 방금 성공한 옛 offer로 화면을 덮어써 새 offer를 지우면 안 된다.
        if (current.pendingOffer?.offerId !== requestedOfferId) {
          return { submitting: false };
        }
        return {
          pendingOffer: null,
          awaitingBoormi: {
            offerId: requestedOfferId,
            orderId: requestedOrderId,
            itemName: requestedItemName,
            acceptedAt: new Date(acceptedAt).toISOString(),
            expiresAt: new Date(acceptedAt + BOORMI_CONFIRM_TTL_MS).toISOString(),
          },
          submitting: false,
        };
      });
      return requestedOrderId;
    } catch (e) {
      set({ submitting: false, message: toMessage(e, "수락에 실패했어요.") });
      return null;
    }
  },

  rejectOffer: async () => {
    const { pendingOffer, submitting } = get();
    if (!pendingOffer || submitting) return;
    const requestedOfferId = pendingOffer.offerId;
    set({ submitting: true });
    try {
      await api.rejectOffer(requestedOfferId);
      bumpMatchingRevision();
      // accept와 같은 이유로, 응답을 기다리는 사이 교체된 새 offer는 건드리지 않는다.
      set((current) => ({
        pendingOffer:
          current.pendingOffer?.offerId === requestedOfferId ? null : current.pendingOffer,
      }));
    } catch (e) {
      set({ message: toMessage(e, "거절에 실패했어요.") });
    } finally {
      set({ submitting: false });
    }
  },

  confirmDreami: async () => {
    const { incomingDreami, submitting } = get();
    if (!incomingDreami || submitting) return null;
    set({ submitting: true });
    try {
      // 주문 상태 전이(IN_PROGRESS)와 매칭 이력까지 처리하는 정식 확정 경로.
      await api.confirmDreami(incomingDreami.orderId, {
        offerId: incomingDreami.offerId,
      });
      bumpMatchingRevision();
      set({ incomingDreami: null, submitting: false });
      return incomingDreami.orderId;
    } catch (e) {
      set({ submitting: false, message: toMessage(e, "확정에 실패했어요.") });
      return null;
    }
  },

  rejectDreami: async () => {
    const { incomingDreami, submitting } = get();
    if (!incomingDreami || submitting) return;
    set({ submitting: true });
    try {
      await api.rejectDreami(incomingDreami.orderId, {
        offerId: incomingDreami.offerId,
      });
      bumpMatchingRevision();
      set({ incomingDreami: null });
    } catch (e) {
      set({ message: toMessage(e, "거절에 실패했어요.") });
    } finally {
      set({ submitting: false });
    }
  },

  clearMessage: () => set({ message: null }),

  syncCurrentMatching: async () => {
    // 이미 조회가 진행 중이면 중복 요청하지 않는다(3초 poll이 느린 응답 위로 쌓이지 않게).
    if (syncing) return;
    syncing = true;
    // 요청 시작 시점의 revision을 저장해둔다. 응답이 오는 사이 SSE 등으로 offer 상태가 바뀌면
    // (아래에서 값이 달라진 것으로 확인되면) 이 snapshot의 pendingOffer/incomingDreami는 버린다.
    const requestedRevision = matchingRevision;
    let data: CurrentMatchingStatusDto;
    try {
      ({ result: data = {} } = await api.getCurrentStatus());
    } catch {
      // 조회 실패는 다음 poll에서 다시 시도한다(팝업 상태는 건드리지 않는다).
      return;
    } finally {
      syncing = false;
    }

    // 드리미 온라인 여부는 서버(매칭엔진 등록 상태)가 진실 소스다. 이 스토어는 메모리에만 있어
    // 새로고침하면 online이 false로 돌아가는데 서버 등록은 살아 있다. 그대로 두면 화면은 오프라인인데
    // 오퍼는 계속 오고, 종료 버튼이 online에 묶여 있어 빠져나갈 수도 없다.
    // 전환 요청이 날아가 있는 동안에는 방금 누른 조작을 되돌리게 되므로 건너뛴다.
    // offer revision과는 별개 관심사이므로, offer 상태가 그 사이 바뀌었어도 온라인 여부는 그대로 반영한다.
    if (
      data.dreamiOnline !== undefined &&
      !togglingOnline &&
      get().online !== data.dreamiOnline
    ) {
      set({ online: data.dreamiOnline });
    }

    // 응답을 기다리는 사이 SSE 등으로 offer 상태가 이미 바뀌었으면, 이 snapshot은 그 시점 기준으로도
    // 낡은 것이다 — pendingOffer/incomingDreami에는 반영하지 않고 버린다(다음 poll이 다시 맞춘다).
    if (matchingRevision !== requestedRevision) return;

    // 수락·거절 요청이 진행 중이면 사용자의 조작과 충돌하지 않도록 스냅샷을 덮어쓰지 않는다.
    if (get().submitting) return;

    // 드리미: 응답 대기 중인 제안. offerId가 바뀔 때만 갱신하고, 없으면 남은 팝업을 제거한다.
    const pending = data.pendingOffer;
    if (pending?.offerId && pending.orderSummary) {
      if (get().pendingOffer?.offerId !== pending.offerId) {
        set({ pendingOffer: pendingOfferFromSnapshot(pending, get().nearbyCalls), message: null });
      }
    } else if (get().pendingOffer) {
      set({ pendingOffer: null });
    }

    // 부르미: 확인 대기 중인 드리미 수락. offerId가 바뀔 때만 receiveDreamiInfo로 재사용(프로필까지 보충).
    const incoming = data.incomingDreami;
    if (incoming?.offerId) {
      if (get().incomingDreami?.offerId !== incoming.offerId) {
        get().receiveDreamiInfo(incoming);
      }
    } else if (get().incomingDreami) {
      set({ incomingDreami: null });
    }
  },

  startMatchingPolling: () => {
    if (pollTimer) return;
    // 장애를 감지한 즉시 한 번 맞추고, 이후 주기적으로 되찾는다.
    void get().syncCurrentMatching();
    pollTimer = setInterval(() => {
      void get().syncCurrentMatching();
    }, MATCHING_POLL_INTERVAL_MS);
  },

  stopMatchingPolling: () => {
    if (pollTimer) {
      clearInterval(pollTimer);
      pollTimer = null;
    }
  },
}));

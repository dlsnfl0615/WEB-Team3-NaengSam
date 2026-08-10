import { create } from "zustand";
import {
  api,
  isApiError,
  type DreamiProfileDto,
  type NearbyCallDto,
} from "@/shared/api";
import type { Coords } from "@/shared/ui";

/** 주변 콜 조회 반경(m)·최대 건수(백엔드 상한 10). */
const NEARBY_RADIUS_M = 3000;
const NEARBY_COUNT = 10;
/** 위치 취득 제한시간(ms). 넘기면 실패로 보고 사용자에게 재시도를 시킨다. */
const GEOLOCATION_TIMEOUT_MS = 10_000;

/** 백엔드 SSE `offer_popup` payload. */
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
  imageKey: string | null;
  ttlSeconds: number;
}

/** 드리미가 받은 제안. 픽업 거리만 주변 콜 캐시에서 보충한다. */
export interface PendingOffer extends OfferPopupPayload {
  distanceMeters?: number;
}

/** 부르미가 받은 드리미 수락 알림(SSE `dreami_info`) + 드리미 프로필. */
export interface IncomingDreami {
  offerId: string;
  orderId: string;
  dreamiId: string;
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
  /** 드리미 화면 진입 시(및 폴링 시) 실행: 좌표를 구해 주변 콜을 조회한다. 온라인 여부와 무관하게 항상 동작한다. */
  loadNearbyCalls: () => Promise<void>;
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
      reject(new Error("이 브라우저에서는 위치를 사용할 수 없어요."));
      return;
    }
    navigator.geolocation.getCurrentPosition(
      ({ coords }) =>
        resolve({ latitude: coords.latitude, longitude: coords.longitude }),
      (error) => reject(new Error(geolocationMessage(error))),
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
  message: null,
  submitting: false,

  // 드리미: 새 제안 도착. 표시 정보는 SSE payload를 쓰고 거리만 주변 콜 캐시에서 보충한다.
  receiveOfferPopup: (payload) => {
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

  // 드리미: 제안 마감(선착순 마감·거절 처리 완료 등).
  receiveOfferClosed: (payload) => {
    const { offerId, reason } = payload as { offerId: string; reason?: string };
    set((s) =>
      s.pendingOffer?.offerId === offerId
        ? { pendingOffer: null, message: reason ?? "제안이 마감됐어요." }
        : {},
    );
  },

  // 드리미: 부르미가 거절함.
  receiveBoormiRejected: (payload) => {
    const { offerId } = payload as { offerId: string };
    set((s) => ({
      pendingOffer: s.pendingOffer?.offerId === offerId ? null : s.pendingOffer,
      message: "부르미가 요청을 거절했어요.",
    }));
  },

  // 부르미: 드리미가 수락 → 프로필을 붙여 확정 카드를 띄운다.
  receiveDreamiInfo: (payload) => {
    const { offerId, orderId, dreamiId } = payload as {
      offerId: string;
      orderId: string;
      dreamiId: string;
    };
    set({ incomingDreami: { offerId, orderId, dreamiId }, message: null });
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

  receiveOfferError: (payload) => {
    const { message } = payload as { message?: string };
    set({ message: message ?? "매칭 요청 처리에 실패했어요." });
  },

  clearOffers: () =>
    set({
      pendingOffer: null,
      incomingDreami: null,
    }),

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
    } catch (e) {
      set({ nearbyCallsError: toMessage(e, "주변 콜을 불러오지 못했어요.") });
    }
  },

  goOnline: async () => {
    // 이미 온라인이면 재등록하지 않는다(서버가 "이미 등록된 드리미입니다"로 응답한다).
    if (get().online) return;

    // loadNearbyCalls가 이미 구해둔 좌표를 재사용한다(위치를 다시 물어 실패하는 경로를 없앤다).
    let coords = get().myLocation;
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
    }
  },

  goOffline: async () => {
    try {
      await api.goOffline();
      set({ online: false, pendingOffer: null });
    } catch (e) {
      set({ message: toMessage(e, "오프라인 전환에 실패했어요.") });
    }
  },

  acceptOffer: async () => {
    const { pendingOffer, submitting } = get();
    if (!pendingOffer || submitting) return null;
    set({ submitting: true });
    try {
      await api.acceptOffer(pendingOffer.offerId);
      set({ pendingOffer: null, submitting: false });
      return pendingOffer.orderId;
    } catch (e) {
      set({ submitting: false, message: toMessage(e, "수락에 실패했어요.") });
      return null;
    }
  },

  rejectOffer: async () => {
    const { pendingOffer, submitting } = get();
    if (!pendingOffer || submitting) return;
    set({ submitting: true });
    try {
      await api.rejectOffer(pendingOffer.offerId);
      set({ pendingOffer: null });
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
      set({ incomingDreami: null });
    } catch (e) {
      set({ message: toMessage(e, "거절에 실패했어요.") });
    } finally {
      set({ submitting: false });
    }
  },

  clearMessage: () => set({ message: null }),
}));

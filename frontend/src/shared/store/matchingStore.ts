import { create } from "zustand";
import {
  api,
  isApiError,
  type DreamiProfileDto,
  type NearbyCallDto,
} from "@/shared/api";

/** 주변 콜 조회 반경(m)·최대 건수(백엔드 상한 10). */
const NEARBY_RADIUS_M = 3000;
const NEARBY_COUNT = 10;

/** 드리미가 받은 제안(SSE `offer_popup`). */
export interface PendingOffer {
  offerId: string;
  orderId: string;
  /** 주변 콜 캐시에서 찾은 주문 정보(없으면 undefined — 금액·물품 미표시). */
  call?: NearbyCallDto;
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
  /** 주변 콜 목록. 제안 팝업의 주문 정보 소스로도 쓴다. */
  nearbyCalls: NearbyCallDto[];
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
  goOnline: () => Promise<void>;
  goOffline: () => Promise<void>;
  loadNearbyCalls: () => Promise<void>;
  /** 드리미 제안 수락 → 성공 시 orderId 반환(실패 시 null). */
  acceptOffer: () => Promise<string | null>;
  rejectOffer: () => Promise<void>;
  /** 부르미 최종 확정 → 성공 시 orderId 반환(실패 시 null). */
  confirmDreami: () => Promise<string | null>;
  rejectDreami: () => Promise<void>;
  clearMessage: () => void;
}

/** 현재 좌표. 권한 거부·미지원이면 reject한다(임의 좌표를 서버에 넣지 않는다). */
function getCurrentPosition(): Promise<GeolocationPosition> {
  return new Promise((resolve, reject) => {
    if (!navigator.geolocation) {
      reject(new Error("이 브라우저에서는 위치를 사용할 수 없어요."));
      return;
    }
    navigator.geolocation.getCurrentPosition(resolve, () =>
      reject(
        new Error("위치 권한이 필요해요. 권한을 허용한 뒤 다시 시도해주세요."),
      ),
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
 * - 드리미: `goOnline` → `offer_popup` 수신 → `acceptOffer`/`rejectOffer`
 * - 부르미: 콜 등록(boormiOrderStore) → `dreami_info` 수신 → `confirmDreami`/`rejectDreami`
 */
export const useMatchingStore = create<MatchingState>((set, get) => ({
  online: false,
  nearbyCalls: [],
  pendingOffer: null,
  incomingDreami: null,
  message: null,
  submitting: false,

  // 드리미: 새 제안 도착. 주문 정보는 주변 콜 캐시에서 채운다.
  receiveOfferPopup: (payload) => {
    const { offerId, orderId } = payload as {
      offerId: string;
      orderId: string;
    };
    set((s) => ({
      pendingOffer: {
        offerId,
        orderId,
        call: s.nearbyCalls.find((c) => c.orderId === orderId),
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

  clearOffers: () => set({ pendingOffer: null, incomingDreami: null }),

  goOnline: async () => {
    try {
      const { coords } = await getCurrentPosition();
      await api.goOnline({
        latitude: coords.latitude,
        longitude: coords.longitude,
      });
      set({ online: true });
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

  loadNearbyCalls: async () => {
    try {
      const { coords } = await getCurrentPosition();
      const { result } = await api.findNearbyCalls({
        lat: coords.latitude,
        lng: coords.longitude,
        radius: NEARBY_RADIUS_M,
        count: NEARBY_COUNT,
      });
      set({ nearbyCalls: result ?? [] });
    } catch (e) {
      set({ message: toMessage(e, "주변 콜을 불러오지 못했어요.") });
    }
  },

  acceptOffer: async () => {
    const { pendingOffer, submitting } = get();
    if (!pendingOffer || submitting) return null;
    set({ submitting: true });
    try {
      await api.acceptByDreami(pendingOffer.offerId);
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
      await api.rejectByDreami(pendingOffer.offerId);
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
      await api.rejectByBoormi(incomingDreami.offerId);
      set({ incomingDreami: null });
    } catch (e) {
      set({ message: toMessage(e, "거절에 실패했어요.") });
    } finally {
      set({ submitting: false });
    }
  },

  clearMessage: () => set({ message: null }),
}));

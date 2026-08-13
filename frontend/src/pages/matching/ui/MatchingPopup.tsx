import {useEffect} from "react";
import {useLocation, useNavigate} from "react-router-dom";
import {ROUTES} from "@/shared/config/routes";
import {type SseHandlers, useExpiryCountdown, useSse} from "@/shared/lib";
import {Toast, type Coords} from "@/shared/ui";
import type {DeliveryStatusResponseDto} from "@/shared/api";
import {useMatchingStore} from "@/shared/store/matchingStore";
import {useSessionStore} from "@/shared/store/sessionStore";
import {useBoormiOrderStore} from "@/shared/store/boormiOrderStore";
import {CallCard} from "./CallCard";
import {OfferCard} from "./OfferCard";

/** 거리(m) → 표시 라벨. */
function formatDistance(meters?: number | null): string {
    if (meters == null) return "-";
    return meters >= 1000
        ? `${(meters / 1000).toFixed(1)}km`
        : `${Math.round(meters)}m`;
}

/** 주문 식별자 → 콜 번호 라벨. */
function formatCode(orderId: string): string {
    return `#${orderId.slice(0, 8)}`;
}

/** 장소 별칭을 우선하고, 없으면 기본주소를 표시한다. */
function formatPlace(
    alias: string | null,
    address: string | null,
    fallback: string,
): string {
    return alias?.trim() || address?.trim() || fallback;
}

/** nullable 위·경도를 지도 컴포넌트 좌표로 변환한다. */
function toCoords(
    latitude: number | null,
    longitude: number | null,
): Coords | undefined {
    if (latitude == null || longitude == null) return undefined;
    return {latitude, longitude};
}

/**
 * 전역 매칭 팝업. 백엔드 SSE로 받은 제안이 있으면 어느 화면에서든 바텀에 뜬다.
 * - 드리미: `offer_popup` 수신 → 콜 카드(수락). 배달 추적 화면 이동은 `delivery_started_dreami` 수신 시.
 * - 부르미: `dreami_info` 수신 → 드리미 카드(확정). 배달 상세 화면 이동은 `delivery_started_boormi` 수신 시.
 * 뒤 화면을 가리지 않는 비차단 오버레이(App 루트에 마운트).
 */
export function MatchingPopup() {
    const navigate = useNavigate();
    const {pathname} = useLocation();
    const isAuthenticated = useSessionStore((s) => s.isAuthenticated);
    const pendingOffer = useMatchingStore((s) => s.pendingOffer);
    const dreamiCoords = useMatchingStore((s) => s.myLocation);
    const incomingDreami = useMatchingStore((s) => s.incomingDreami);
    const submitting = useMatchingStore((s) => s.submitting);
    const acceptOffer = useMatchingStore((s) => s.acceptOffer);
    const rejectOffer = useMatchingStore((s) => s.rejectOffer);
    const confirmDreami = useMatchingStore((s) => s.confirmDreami);
    const rejectDreami = useMatchingStore((s) => s.rejectDreami);
    const clearOffers = useMatchingStore((s) => s.clearOffers);
    const message = useMatchingStore((s) => s.message);
    const clearMessage = useMatchingStore((s) => s.clearMessage);
    const expirePendingOffer = useMatchingStore((s) => s.expirePendingOffer);
    const expireIncomingDreami = useMatchingStore((s) => s.expireIncomingDreami);

    // 카드가 없어도(call/offer가 null) 항상 최상단에서 호출한다 — 훅이 그 경우 스스로 비활성 상태를 반환한다.
    const callCountdown = useExpiryCountdown(pendingOffer?.offeredAt, pendingOffer?.expiresAt, {
        onExpire: () => {
            if (pendingOffer) expirePendingOffer(pendingOffer.offerId);
        },
    });
    const offerCountdown = useExpiryCountdown(incomingDreami?.acceptedAt, incomingDreami?.expiresAt, {
        onExpire: () => {
            if (incomingDreami) expireIncomingDreami(incomingDreami.offerId);
        },
    });

    // 매칭 이벤트는 스토어 액션에 위임한다(배달 진행 중 이벤트는 각 배달 화면이 구독).
    // delivery_started_* 는 배달이 실제로 시작됐다는 서버 신호이며, 이때 비로소 배달 화면으로 이동한다.
    const handlers: SseHandlers = {
        offer_popup: useMatchingStore.getState().receiveOfferPopup,
        offer_closed: useMatchingStore.getState().receiveOfferClosed,
        boormi_rejected: useMatchingStore.getState().receiveBoormiRejected,
        dreami_info: useMatchingStore.getState().receiveDreamiInfo,
        offer_error: useMatchingStore.getState().receiveOfferError,
        delivery_started_dreami: (payload) => {
            const {orderId} = payload as DeliveryStatusResponseDto;
            // BE가 이 시점에 드리미를 매칭 후보·대기열에서 이미 제거했으므로, FE도 온라인 상태를 맞춘다.
            // 드리미가 의도적으로 오프라인 전환한 게 아니므로 goOffline API는 호출하지 않고 로컬 상태만 갱신한다.
            useMatchingStore.setState({online: false, pendingOffer: null});
            navigate(`${ROUTES.deliveryTrack}?orderId=${orderId}`, {replace: true});
        },
        delivery_started_boormi: (payload) => {
            const {orderId} = payload as DeliveryStatusResponseDto;
            navigate(`${ROUTES.deliveryDetail}?orderId=${orderId}`, {replace: true});
        },
    };

    // 구독은 세션 쿠키 기반이므로 로그인 상태에서만 연결한다.
    useSse(handlers, {enabled: isAuthenticated});

    // 로그아웃 시 떠 있던 팝업을 남기지 않는다.
    useEffect(() => {
        if (!isAuthenticated) clearOffers();
    }, [isAuthenticated, clearOffers]);

    // 거절/사유 선택 화면에서는 팝업을 숨겨 화면 접근을 막지 않는다.
    const onReasonScreen =
        pathname === ROUTES.rejectReason || pathname === ROUTES.driverReason;
    if (onReasonScreen) return null;

    const call = pendingOffer;
    const offer = incomingDreami;

    // 카드가 없어도 매칭 안내(제안 마감·상대 거절 등)는 토스트로 알려야 한다.
    if (!call && !offer) {
        if (!message) return null;
        return (
            <div className="pointer-events-none fixed inset-x-0 bottom-0 z-40 flex justify-center">
                <div
                    className="ds-sheet-up pointer-events-auto w-full max-w-[420px] px-4 pb-[calc(env(safe-area-inset-bottom,0px)+1rem)]">
                    <Toast
                        title={message}
                        action={
                            <button
                                type="button"
                                onClick={clearMessage}
                                className="shrink-0 text-xs font-semibold text-track"
                            >
                                닫기
                            </button>
                        }
                    />
                </div>
            </div>
        );
    }

    const onAcceptCall = async () => {
        await acceptOffer();
        // 이동은 하지 않는다 — 배달이 실제로 시작되면 delivery_started_dreami SSE가 이동시킨다.
    };

    const onAcceptOffer = async () => {
        await confirmDreami();
        // 확정으로 주문 상태가 IN_PROGRESS로 바뀌므로 목록을 다시 불러온다.
        await useBoormiOrderStore.getState().load();
        // 이동은 하지 않는다 — 배달이 실제로 시작되면 delivery_started_boormi SSE가 이동시킨다.
    };

    return (
        <div className="fixed inset-0 z-40 flex items-end justify-center">
            <div aria-hidden className="absolute inset-0 bg-ink/40"/>
            <div
                className="ds-sheet-up relative w-full max-w-[420px] px-4 pb-[calc(env(safe-area-inset-bottom,0px)+1rem)]">
                {call ? (
                    <CallCard
                        code={formatCode(call.orderId)}
                        price={
                            call.deliveryAmount != null
                                ? `₩${call.deliveryAmount.toLocaleString()}`
                                : "금액 확인 중"
                        }
                        place={call.itemName ?? "물품 배송"}
                        route={`${formatPlace(
                            call.originAlias,
                            call.originAddressLine1,
                            "출발지",
                        )} → ${formatPlace(
                            call.destinationAlias,
                            call.destinationAddressLine1,
                            "도착지",
                        )}`}
                        pickup={toCoords(
                            call.originLatitude,
                            call.originLongitude,
                        )}
                        dropoff={toCoords(
                            call.destinationLatitude,
                            call.destinationLongitude,
                        )}
                        currentLocation={dreamiCoords ?? undefined}
                        deliveryDistance={formatDistance(call.deliveryDistance)}
                        eta={`${call.deliveryEta}분`}
                        requestNote={call.deliveryRequest ?? undefined}
                        countdown={callCountdown}
                        onReject={rejectOffer}
                        onAccept={onAcceptCall}
                    />
                ) : (
                    offer && (
                        <OfferCard
                            heading="새 드리미 요청 도착!"
                            name={
                                offer.profile?.name
                                    ? `드리미 '${offer.profile.name}'`
                                    : "드리미 정보 확인 중"
                            }
                            rating={offer.profile?.dreamiAvgScore ?? 0}
                            pickupEtaMinutes={offer.pickupEtaMinutes ?? null}
                            countdown={offerCountdown}
                            onReject={rejectDreami}
                            onAccept={onAcceptOffer}
                        />
                    )
                )}
                {submitting && (
                    <p className="pt-2 text-center text-2xs text-white">처리 중…</p>
                )}
            </div>
        </div>
    );
}

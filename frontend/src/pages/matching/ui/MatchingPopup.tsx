import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { ROUTES } from "@/shared/config/routes";
import { useRole } from "@/shared/lib/role/useRole";
import {
  useActiveDelivery,
  useDeliveryStore,
} from "@/shared/store/deliveryStore";
import { getCalls, getOffers } from "@/shared/mock/matchingService";
import type { Call, Offer } from "@/shared/mock/types";
import { CallCard } from "./CallCard";
import { OfferCard } from "./OfferCard";

/** 매칭 요청 팝업 주기(ms). */
const POPUP_INTERVAL_MS = 3000;

/**
 * 전역 매칭 팝업. 진행 중인 매칭이 있으면 어느 화면에서든 바텀에 살짝 뜬다.
 * - 부르미: 활성 배달이 "매칭중"이면 드리미 오퍼 팝업.
 * - 드리미: 콜 탐색 중(seeking)이면 콜 팝업.
 * 뒤 화면을 가리지 않는 비차단 오버레이(App 루트에 마운트).
 */
export function MatchingPopup() {
  const navigate = useNavigate();
  const { role } = useRole();
  const active = useActiveDelivery();
  const seeking = useDeliveryStore((s) => s.seeking);
  const acceptCall = useDeliveryStore((s) => s.acceptCall);
  const acceptOffer = useDeliveryStore((s) => s.acceptOffer);

  const [calls, setCalls] = useState<Call[]>([]);
  const [offers, setOffers] = useState<Offer[]>([]);
  const [tick, setTick] = useState(0);

  const isDriver = role === "드리미";
  const showSender = !isDriver && active?.status === "매칭중";
  const showDriver = isDriver && seeking;
  const visible = showSender || showDriver;

  // 대기 목록 1회 로드.
  useEffect(() => {
    getOffers().then(setOffers);
    getCalls().then(setCalls);
  }, []);

  // 표시 조건이 참일 때만 3초마다 새 요청 팝업.
  useEffect(() => {
    if (!visible) return;
    const timer = setInterval(() => setTick((t) => t + 1), POPUP_INTERVAL_MS);
    return () => clearInterval(timer);
  }, [visible]);

  const call =
    showDriver && tick > 0 && calls.length > 0
      ? calls[(tick - 1) % calls.length]
      : undefined;
  const offer =
    showSender && tick > 0 && offers.length > 0
      ? offers[(tick - 1) % offers.length]
      : undefined;

  if (!call && !offer) return null;

  const onAcceptCall = async () => {
    if (!call) return;
    await acceptCall(call);
    navigate(ROUTES.deliveryTrack, { replace: true });
  };

  const onAcceptOffer = async () => {
    if (!offer) return;
    await acceptOffer(offer.name);
    navigate(ROUTES.deliveryDetail, { replace: true });
  };

  return (
    <div className="pointer-events-none fixed inset-x-0 bottom-0 z-40 flex justify-center">
      <div className="ds-sheet-up pointer-events-auto w-full max-w-[420px] px-4 pb-[calc(env(safe-area-inset-bottom,0px)+1rem)]">
        {call ? (
          <CallCard
            code={call.code}
            price={`₩${call.price.toLocaleString()}`}
            place={call.place}
            route={call.route}
            pickupDistance={call.pickupDistance}
            dropoffDistance={call.dropoffDistance}
            itemType={call.itemType}
            onReject={() => navigate(ROUTES.rejectReason)}
            onAccept={onAcceptCall}
          />
        ) : (
          offer && (
            <OfferCard
              heading="새 드리미 요청 도착!"
              name={`드리미 '${offer.name}'`}
              rating={offer.rating}
              countLabel="배송"
              count={offer.count}
              distance={offer.distance}
              onReject={() => navigate(ROUTES.rejectReason)}
              onAccept={onAcceptOffer}
            />
          )
        )}
      </div>
    </div>
  );
}

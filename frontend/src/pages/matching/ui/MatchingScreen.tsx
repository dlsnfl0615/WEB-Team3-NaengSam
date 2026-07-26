import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { Card, MapCard, Modal, ScreenShell, TopBar } from "@/shared/ui";
import { ROUTES } from "@/shared/config/routes";
import { useRole } from "@/shared/lib/role/useRole";
import { CallCard } from "./CallCard";
import { OfferCard } from "./OfferCard";

/**
 * 매칭(찾는 중) 화면(Figma node 191:763).
 * 지도 위에서 대기 상태를 보여주고, 도착한 요청을 수락·거절합니다(UI 전용).
 * 현재 역할에 따라 상대가 바뀝니다 — 부르미는 드리미를, 드리미는 부르미를 찾습니다.
 */
export function MatchingScreen() {
  const navigate = useNavigate();
  const { role } = useRole();
  const [offerVisible, setOfferVisible] = useState(true);

  const isDriver = role === "드리미";
  const counterpart = isDriver ? "부르미" : "드리미";

  return (
    <ScreenShell>
      <TopBar
        title={`${counterpart}를 찾는 중`}
        onBack={() => navigate(-1)}
        actions={[]}
      />

      <main className="flex flex-1 flex-col gap-3 pt-4">
        <MapCard height={280} />

        <Card className="flex flex-col gap-1">
          <p className="flex items-center gap-2 text-base font-bold text-navy-900">
            <span className="size-2 rounded-pill bg-teal-500" />
            {isDriver
              ? "근방 300m 내 부름 5건 대기중"
              : "근방 300m 내 드리미 5명 대기중"}
          </p>
          <p className="text-2xs text-muted">
            요청을 보낸 {counterpart}의 수락을 기다리고 있어요...
          </p>
        </Card>
      </main>

      <Modal
        open={offerVisible}
        label={isDriver ? "새 부름 요청" : "새 드리미 요청"}
      >
        {isDriver ? (
          <CallCard
            code="#B-882"
            price="₩3,500"
            place="파르나스 타워"
            route="24F → 12F"
            pickupDistance="120m"
            dropoffDistance="1.2km"
            itemType="음료"
            onReject={() => navigate(ROUTES.rejectReason)}
            onAccept={() => setOfferVisible(false)}
          />
        ) : (
          <OfferCard
            heading="새 드리미 요청 도착!"
            name="드리미 '핀'"
            rating={4.9}
            countLabel="배송"
            count={132}
            distance="120m"
            onReject={() => navigate(ROUTES.rejectReason)}
            onAccept={() => setOfferVisible(false)}
          />
        )}
      </Modal>
    </ScreenShell>
  );
}

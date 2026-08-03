import { useNavigate, useSearchParams } from "react-router-dom";
import { ScreenShell, TopBar } from "@/shared/ui";
import { useDeliveryById } from "@/shared/store/deliveryStore";
import { CompletedDetail } from "./CompletedDetail";
import { OngoingDetail } from "./OngoingDetail";

/**
 * 배달 내역 상세 화면(Figma node 191:1063, 191:1267).
 * ?status=진행중 이면 실시간 추적 본문을, 그 외에는 완료 본문을 보여줍니다.
 * ?id=<배달 id>로 특정 배달을 구독합니다.
 */
export function ActivityDetailScreen() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const ongoing = params.get("status") === "진행중";
  const delivery = useDeliveryById(params.get("id"));

  return (
    <ScreenShell>
      <TopBar
        title="배달 상세"
        onBack={() => navigate(-1)}
        actions={ongoing ? [] : ["more"]}
      />

      <main className="flex flex-1 flex-col gap-4 pt-4">
        {ongoing ? (
          <OngoingDetail delivery={delivery} onCancel={() => navigate(-1)} />
        ) : (
          <CompletedDetail delivery={delivery} />
        )}
      </main>
    </ScreenShell>
  );
}

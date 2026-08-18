import { useSearchParams } from "react-router-dom";
import { useBackOrHome } from "@/shared/lib/navigation/useBackOrHome";
import { ScreenShell, TopBar } from "@/shared/ui";
import { useDeliveryById } from "@/shared/store/deliveryStore";
import { useBoormiOrderById } from "@/shared/store/boormiOrderStore";
import { CompletedDetail } from "./CompletedDetail";
import { OngoingDetail } from "./OngoingDetail";

/**
 * 배달 내역 상세 화면(Figma node 191:1063, 191:1267).
 * ?status=진행중 이면 실시간 추적 본문(아직 mock)을, 그 외에는 완료 본문(실 데이터)을 보여줍니다.
 * ?id=<주문 id>로 특정 배달을 구독합니다.
 */
export function ActivityDetailScreen() {
  const backOrHome = useBackOrHome();
  const [params] = useSearchParams();
  const ongoing = params.get("status") === "진행중";
  const id = params.get("id");
  const mockDelivery = useDeliveryById(id);
  const { order, loading: ordersLoading } = useBoormiOrderById(id);

  return (
    <ScreenShell>
      <TopBar title="배달 상세" onBack={backOrHome} actions={[]} />

      <main className="flex flex-1 flex-col gap-4 pt-4">
        {ongoing ? (
          <OngoingDetail delivery={mockDelivery} onCancel={backOrHome} />
        ) : order ? (
          <CompletedDetail order={order} />
        ) : ordersLoading ? (
          <p className="py-10 text-center text-sm text-muted">불러오는 중…</p>
        ) : (
          <p className="py-10 text-center text-sm text-muted">
            내역을 찾을 수 없어요.
          </p>
        )}
      </main>
    </ScreenShell>
  );
}

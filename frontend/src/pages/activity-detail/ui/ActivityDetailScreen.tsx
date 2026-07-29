import { useNavigate, useSearchParams } from "react-router-dom";
import { ScreenShell, TopBar } from "@/shared/ui";
import { CompletedDetail } from "./CompletedDetail";
import { OngoingDetail } from "./OngoingDetail";

/**
 * 배달 내역 상세 화면(Figma node 191:1063, 191:1267).
 * ?status=진행중 이면 실시간 추적 본문을, 그 외에는 완료 본문을 보여줍니다(UI 전용).
 */
export function ActivityDetailScreen() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const ongoing = params.get("status") === "진행중";

  return (
    <ScreenShell>
      <TopBar
        title="배달 상세"
        onBack={() => navigate(-1)}
        actions={ongoing ? [] : ["more"]}
      />

      <main className="flex flex-1 flex-col gap-4 pt-4">
        {ongoing ? (
          <OngoingDetail onCancel={() => navigate(-1)} />
        ) : (
          <CompletedDetail />
        )}
      </main>
    </ScreenShell>
  );
}

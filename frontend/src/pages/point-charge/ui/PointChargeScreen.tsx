import { useNavigate, useSearchParams } from "react-router-dom";
import { ScreenShell, TopBar } from "@/shared/ui";
import { ChargeForm } from "./ChargeForm";
import { ConvertForm } from "./ConvertForm";

/**
 * 포인트 충전·전환 화면(Figma node 191:1475, 191:1532).
 * ?mode=convert 이면 머니→포인트 전환을, 그 외에는 카드 충전을 보여줍니다.
 */
export function PointChargeScreen() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const convert = params.get("mode") === "convert";

  return (
    <ScreenShell>
      <TopBar
        title={convert ? "포인트로 전환" : "포인트 충전"}
        onBack={() => navigate(-1)}
        actions={[]}
      />

      <main className="flex flex-1 flex-col gap-4 pt-4">
        {convert ? <ConvertForm /> : <ChargeForm />}
      </main>
    </ScreenShell>
  );
}

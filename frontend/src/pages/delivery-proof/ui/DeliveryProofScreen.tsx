import { useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { Button, Card, IconChip, ScreenShell, TopBar } from "@/shared/ui";
import { ROUTES } from "@/shared/config/routes";
import { isApiError } from "@/shared/api";
import { axiosInstance } from "@/shared/api/http/axiosInstance";
import { ProofPhoto } from "./ProofPhoto";
import { ProofSignature } from "./ProofSignature";
import { PickupProof } from "./PickupProof";

/**
 * 배송 완료 인증 화면(Figma node 191:1322, 191:1352).
 * ?mode=photo 면 사진 인증, 기본은 수령인 서명 인증입니다(UI 전용).
 *
 * 실 백엔드 모드: `?orderId=&intent=pickup` 이 있으면 /delivery-test → /delivery-track 에서 넘어온
 * 실제 픽업 인증이다. 실제 사진 파일을 골라 presign(GET /upload/url) → dev-storage PUT →
 * pickup-finish(POST) 를 순서대로 호출해 배달중으로 전이시키고, 배송중 상태의 추적 화면으로 돌아간다.
 */
export function DeliveryProofScreen() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const [memo, setMemo] = useState("");

  const isPhoto = params.get("mode") === "photo";
  const orderId = params.get("orderId");
  const isRealPickup = params.get("intent") === "pickup" && Boolean(orderId);

  if (isRealPickup && orderId) {
    return <RealPickupProof orderId={orderId} />;
  }

  return (
    <ScreenShell>
      <TopBar title="배송 완료 인증" onBack={() => navigate(-1)} actions={[]} />

      <main className="flex flex-1 flex-col gap-4 pt-4">
        <Card className="flex flex-col items-center gap-2">
          <IconChip name={isPhoto ? "package" : "document"} size={36} />
          <p className="text-base font-bold text-navy-900">
            {isPhoto ? "소형택배 #B-773" : "서류 배송 #B-771"}
          </p>
          <p className="text-2xs text-muted">
            {isPhoto ? "C동 7F 문 앞" : "B동 405호 · 수령인 '민'"}
          </p>
        </Card>

        <h1 className="text-lg font-bold tracking-[-0.4px] text-navy-900">
          {isPhoto ? (
            <>
              놓아둔 위치를
              <br />
              촬영해주세요
            </>
          ) : (
            <>
              수령인 서명을
              <br />
              받아주세요
            </>
          )}
        </h1>

        {isPhoto ? (
          <ProofPhoto memo={memo} onMemoChange={setMemo} />
        ) : (
          <ProofSignature />
        )}

        <Button
          variant="navy"
          block
          onClick={() => navigate(ROUTES.deliveryComplete, { replace: true })}
        >
          {isPhoto ? "사진 첨부 · 배송 종료" : "서명 완료 · 배송 종료"}
        </Button>
      </main>
    </ScreenShell>
  );
}

/**
 * 실제 픽업 인증: 파일 선택 → presign → dev-storage PUT → pickup-finish.
 * 세 호출 모두 공통 axios 인스턴스로 직접 호출한다(생성 클라이언트의 upload/delivery 시그니처가
 * 아직 낡아 purpose/resourceId·photoKey 바디를 실을 수 없기 때문 — 임시 테스트 흐름 한정).
 */
function RealPickupProof({ orderId }: { orderId: string }) {
  const navigate = useNavigate();
  const [file, setFile] = useState<File | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleFinish = async () => {
    if (!file) return;
    setLoading(true);
    setError(null);
    try {
      // 1) presign 발급 — 픽업 인증 용도 + 해당 주문(resourceId=orderId) 으로 스코프 지정.
      const fileName = file.name.replace(/[\\/.]{2,}|[\\/]/g, "_") || "pickup.jpg";
      const presign = await axiosInstance.get("/api/v1/upload/url", {
        params: {
          fileName,
          purpose: "PICKUP_CERTIFICATION_IMAGE",
          resourceId: orderId,
        },
      });
      const { url, key } = presign.data?.result ?? {};
      if (!url || !key) throw new Error("presign 발급에 실패했습니다.");

      // 2) 발급 URL 로 실제 업로드. 로컬 dev-storage 는 절대 URL 이라 동일 출처 경로로 바꿔
      //    Vite 프록시를 태운다(CORS 회피). PUT 바디는 원본 파일 바이트.
      const putPath = new URL(url).pathname + new URL(url).search;
      await axiosInstance.put(putPath, file, {
        headers: { "Content-Type": file.type || "application/octet-stream" },
      });

      // 3) 픽업 완료 — photoKey 를 실어 실제 상태 전이(PICKUP_NORMAL → DELIVERING).
      await axiosInstance.post(
        `/api/v1/delivery/orders/${orderId}/pickup-finish`,
        { photoKey: key },
      );

      // 배송중 상태로 추적 화면 복귀.
      navigate(`${ROUTES.deliveryTrack}?orderId=${orderId}&status=DELIVERING`, {
        replace: true,
      });
    } catch (e) {
      setError(isApiError(e) ? e.message : "픽업 완료 처리에 실패했습니다.");
    } finally {
      setLoading(false);
    }
  };

  return (
    <ScreenShell>
      <TopBar title="픽업 인증" onBack={() => navigate(-1)} actions={[]} />

      <main className="flex flex-1 flex-col gap-4 pt-4">
        <Card className="flex flex-col items-center gap-2">
          <IconChip name="package" size={36} />
          <p className="text-base font-bold text-navy-900">픽업 인증 사진</p>
          <p className="break-all text-2xs text-muted">주문 {orderId}</p>
        </Card>

        <h1 className="text-lg font-bold tracking-[-0.4px] text-navy-900">
          픽업한 물품을
          <br />
          촬영해주세요
        </h1>

        <PickupProof
          fileName={file?.name ?? null}
          onFileSelected={(f) => {
            setFile(f);
            setError(null);
          }}
        />

        {error && <p className="text-sm text-status-danger">{error}</p>}

        <Button
          variant="navy"
          block
          disabled={!file || loading}
          onClick={handleFinish}
        >
          {loading ? "처리 중…" : "픽업 완료 · 사진 첨부"}
        </Button>
      </main>
    </ScreenShell>
  );
}

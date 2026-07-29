import { useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { Button, Card, IconChip, ScreenShell, TopBar } from "@/shared/ui";
import { ROUTES } from "@/shared/config/routes";
import { ProofPhoto } from "./ProofPhoto";
import { ProofSignature } from "./ProofSignature";

/**
 * 배송 완료 인증 화면(Figma node 191:1322, 191:1352).
 * ?mode=photo 면 사진 인증, 기본은 수령인 서명 인증입니다(UI 전용).
 */
export function DeliveryProofScreen() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const [memo, setMemo] = useState("");

  const isPhoto = params.get("mode") === "photo";

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
          onClick={() => navigate(ROUTES.deliveryComplete)}
        >
          {isPhoto ? "사진 첨부 · 배송 종료" : "서명 완료 · 배송 종료"}
        </Button>
      </main>
    </ScreenShell>
  );
}

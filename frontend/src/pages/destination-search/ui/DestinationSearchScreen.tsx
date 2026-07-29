import { useNavigate } from "react-router-dom";
import { DestinationPicker, MapCard, ScreenShell, TopBar } from "@/shared/ui";

/**
 * 도착지 검색 화면(Figma node 191:921).
 * 지도 위 시트 형태로 검색 필드·빠른 선택 칩·최근/추천 목록을 제공합니다(UI 전용).
 */
export function DestinationSearchScreen() {
  const navigate = useNavigate();

  return (
    <ScreenShell>
      <MapCard height={180} />

      <div className="pt-4">
        <TopBar
          title="도착지 검색"
          actions={["close"]}
          onAction={() => navigate(-1)}
        />
      </div>

      <main className="flex flex-1 flex-col pt-4">
        <DestinationPicker onSubmit={() => navigate(-1)} />
      </main>
    </ScreenShell>
  );
}

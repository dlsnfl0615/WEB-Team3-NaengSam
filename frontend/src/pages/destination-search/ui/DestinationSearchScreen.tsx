import { useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  Button,
  Icon,
  MapCard,
  ScreenShell,
  SearchField,
  TopBar,
  type IconName,
} from "@/shared/ui";
import { cn } from "@/shared/lib/cn";
import { PlaceItem } from "./PlaceItem";

const QUICK_OPTIONS = ["현재 위치", "지도에서 선택"] as const;

const PLACES: { name: string; detail: string; icon: IconName }[] = [
  { name: "B동 405호", detail: "마케팅팀 사무실", icon: "time" },
  { name: "A동 로비", detail: "1층 안내데스크", icon: "time" },
  { name: "C동 7F 회의실", detail: "즐겨찾기", icon: "star" },
];

/**
 * 도착지 검색 화면(Figma node 191:921).
 * 지도 위 시트 형태로 검색 필드·빠른 선택 칩·최근/추천 목록을 제공합니다(UI 전용).
 */
export function DestinationSearchScreen() {
  const navigate = useNavigate();
  const [keyword, setKeyword] = useState("");
  const [quick, setQuick] =
    useState<(typeof QUICK_OPTIONS)[number]>("현재 위치");
  const [selected, setSelected] = useState(PLACES[0].name);

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

      <main className="flex flex-1 flex-col gap-3 pt-4">
        <SearchField
          placeholder="동·층·호수 또는 이름 검색"
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
        />

        <div role="radiogroup" className="flex gap-2">
          {QUICK_OPTIONS.map((option) => {
            const active = quick === option;
            return (
              <button
                key={option}
                type="button"
                role="radio"
                aria-checked={active}
                onClick={() => setQuick(option)}
                className={cn(
                  "flex items-center gap-1 rounded-pill px-3 py-1 text-xs font-semibold",
                  active ? "bg-teal-500 text-white" : "bg-track text-muted",
                )}
              >
                {option === "현재 위치" && <Icon name="pin" size={12} />}
                {option}
              </button>
            );
          })}
        </div>

        <p className="text-2xs text-muted">최근 · 추천</p>

        <div role="radiogroup" className="flex flex-col gap-2">
          {PLACES.map((place) => (
            <PlaceItem
              key={place.name}
              {...place}
              selected={selected === place.name}
              onSelect={() => setSelected(place.name)}
            />
          ))}
        </div>
      </main>

      <footer className="pt-4">
        <Button variant="navy" block onClick={() => navigate(-1)}>
          이 위치로 설정
        </Button>
      </footer>
    </ScreenShell>
  );
}

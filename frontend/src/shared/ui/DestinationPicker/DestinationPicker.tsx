import { useState } from "react";
import { Button } from "../Button/Button";
import { Icon } from "../Icon/Icon";
import { SearchField } from "../TextField/TextField";
import { cn } from "@/shared/lib/cn";
import { PlaceItem } from "./PlaceItem";
import { PLACES, QUICK_OPTIONS } from "./places";

export interface DestinationPickerProps {
  /** '이 위치로 설정'을 누르면 선택한 장소 이름과 함께 호출됩니다. */
  onSubmit: (place: string) => void;
}

/**
 * 도착지 선택 본문(검색 필드 + 빠른 선택 칩 + 최근·추천 목록 + 확인 버튼).
 * 도착지 검색 화면과 부름 등록의 바텀시트가 함께 사용합니다.
 */
export function DestinationPicker({ onSubmit }: DestinationPickerProps) {
  const [keyword, setKeyword] = useState("");
  const [quick, setQuick] =
    useState<(typeof QUICK_OPTIONS)[number]>("현재 위치");
  const [selected, setSelected] = useState(PLACES[0].name);

  return (
    <div className="flex flex-1 flex-col gap-3">
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

      <div className="mt-auto pt-4">
        <Button variant="navy" block onClick={() => onSubmit(selected)}>
          이 위치로 설정
        </Button>
      </div>
    </div>
  );
}

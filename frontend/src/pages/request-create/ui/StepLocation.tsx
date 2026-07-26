import { useState } from "react";
import {
  BottomSheet,
  Card,
  DestinationPicker,
  Icon,
  TextField,
  TopBar,
} from "@/shared/ui";
import { cn } from "@/shared/lib/cn";
import type { RequestForm, UpdateForm } from "./types";

export interface StepLocationProps {
  form: RequestForm;
  update: UpdateForm;
}

type Field = "pickup" | "dropoff";

const FIELD_LABELS: Record<Field, string> = {
  pickup: "픽업지 검색",
  dropoff: "도착지 검색",
};

/** 스텝 1: 위치 — 픽업/도착지 입력 + 대면/비대면 선택 + 지도(자리표시). */
export function StepLocation({ form, update }: StepLocationProps) {
  const [editing, setEditing] = useState<Field | null>(null);

  return (
    <div className="flex flex-col gap-4">
      <h2 className="text-xl font-bold tracking-[-0.4px] text-navy-900">
        어디로 배송할까요?
      </h2>

      <Card className="flex flex-col gap-3">
        <TextField
          leadingIcon="pin"
          placeholder="픽업지: 사무실 / 구역"
          value={form.pickup}
          readOnly
          aria-haspopup="dialog"
          className="cursor-pointer"
          onClick={() => setEditing("pickup")}
        />
        <MeetingOption
          label={"“대면”으로 드릴게요"}
          selected={form.meeting === "대면"}
          onClick={() => update({ meeting: "대면" })}
        />

        <div className="flex justify-center py-1">
          <Icon name="transfer" size={28} className="text-track" />
        </div>

        <TextField
          leadingIcon="pin"
          placeholder="도착지: 도착 층 / 호수"
          value={form.dropoff}
          readOnly
          aria-haspopup="dialog"
          className="cursor-pointer"
          onClick={() => setEditing("dropoff")}
        />
        <MeetingOption
          label={"“비대면”으로 받을게요"}
          selected={form.meeting === "비대면"}
          onClick={() => update({ meeting: "비대면" })}
        />
      </Card>

      <div className="flex h-[200px] items-center justify-center rounded-md border border-dashed border-line bg-canvas text-center text-2xs leading-[14px] text-muted">
        <p>
          지도 / MAP
          <br />
          주변 드리미: 12명
        </p>
      </div>

      <BottomSheet
        open={editing !== null}
        label={editing ? FIELD_LABELS[editing] : ""}
        onClose={() => setEditing(null)}
      >
        <TopBar
          title={editing ? FIELD_LABELS[editing] : ""}
          actions={["close"]}
          onAction={() => setEditing(null)}
        />
        <DestinationPicker
          onSubmit={(place) => {
            if (editing) update({ [editing]: place });
            setEditing(null);
          }}
        />
      </BottomSheet>
    </div>
  );
}

interface MeetingOptionProps {
  label: string;
  selected: boolean;
  onClick: () => void;
}

function MeetingOption({ label, selected, onClick }: MeetingOptionProps) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        "w-fit text-sm",
        selected ? "font-semibold text-teal-700" : "text-muted",
      )}
    >
      {label}
    </button>
  );
}

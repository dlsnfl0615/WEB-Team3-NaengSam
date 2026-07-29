import { useState } from "react";
import {
  BottomSheet,
  Card,
  DestinationPicker,
  Icon,
  SegmentedToggle,
  TextField,
  TopBar,
} from "@/shared/ui";
import type { Meeting, RequestForm, UpdateForm } from "./types";

export interface StepLocationProps {
  form: RequestForm;
  update: UpdateForm;
}

type Field = "pickup" | "dropoff";

const FIELD_LABELS: Record<Field, string> = {
  pickup: "픽업지 검색",
  dropoff: "도착지 검색",
};

const MEETING_KEYS: Record<Field, "pickupMeeting" | "dropoffMeeting"> = {
  pickup: "pickupMeeting",
  dropoff: "dropoffMeeting",
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
        <MeetingNote meeting={form.pickupMeeting} suffix="드릴게요" />

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
        <MeetingNote meeting={form.dropoffMeeting} suffix="받을게요" />
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

        <div className="flex flex-col gap-2">
          <p className="text-sm text-muted">전달 방식</p>
          <SegmentedToggle
            options={["대면", "비대면"]}
            value={editing ? form[MEETING_KEYS[editing]] : ""}
            onChange={(value) => {
              if (editing)
                update({ [MEETING_KEYS[editing]]: value as Meeting });
            }}
          />
        </div>

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

interface MeetingNoteProps {
  meeting: Meeting;
  suffix: string;
}

/** “대면”으로 드릴게요 — 해당 필드에 선택된 전달 방식을 문장으로 보여줍니다. */
function MeetingNote({ meeting, suffix }: MeetingNoteProps) {
  return (
    <p className="text-sm text-muted">
      <span className="font-semibold text-teal-700">“{meeting}”</span>으로{" "}
      {suffix}
    </p>
  );
}

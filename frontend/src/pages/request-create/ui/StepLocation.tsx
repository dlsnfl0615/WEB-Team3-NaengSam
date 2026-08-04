import { useState } from "react";
import { Card, Icon, TextField } from "@/shared/ui";
import { AddressSheet, type AddressValue } from "./AddressSheet";
import { KakaoMap } from "./KakaoMap";
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

/** 스텝 1: 위치 — 카카오(다음) 주소 검색 + 대면/비대면 선택 + 지도. */
export function StepLocation({ form, update }: StepLocationProps) {
  const [editing, setEditing] = useState<Field | null>(null);

  const values: Record<Field, AddressValue> = {
    pickup: {
      address1: form.pickup,
      detail: form.pickupDetail,
      meeting: form.pickupMeeting,
    },
    dropoff: {
      address1: form.dropoff,
      detail: form.dropoffDetail,
      meeting: form.dropoffMeeting,
    },
  };

  const submitAddress = (field: Field, value: AddressValue) => {
    if (field === "pickup") {
      update({
        pickup: value.address1,
        pickupDetail: value.detail,
        pickupMeeting: value.meeting,
      });
    } else {
      update({
        dropoff: value.address1,
        dropoffDetail: value.detail,
        dropoffMeeting: value.meeting,
      });
    }
    setEditing(null);
  };

  return (
    <div className="flex flex-col gap-4">
      <h2 className="text-xl font-bold tracking-[-0.4px] text-navy-900">
        어디로 배송할까요?
      </h2>

      <Card className="flex flex-col gap-3">
        <TextField
          leadingIcon="pin"
          placeholder="픽업지: 도로명 주소 검색"
          value={[form.pickup, form.pickupDetail].filter(Boolean).join(" ")}
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
          placeholder="도착지: 도로명 주소 검색"
          value={[form.dropoff, form.dropoffDetail].filter(Boolean).join(" ")}
          readOnly
          aria-haspopup="dialog"
          className="cursor-pointer"
          onClick={() => setEditing("dropoff")}
        />
        <MeetingNote meeting={form.dropoffMeeting} suffix="받을게요" />
      </Card>

      <KakaoMap pickup={form.pickup} dropoff={form.dropoff} />

      <AddressSheet
        key={editing ?? "closed"}
        open={editing !== null}
        label={editing ? FIELD_LABELS[editing] : ""}
        value={editing ? values[editing] : { address1: "", detail: "", meeting: "대면" }}
        onClose={() => setEditing(null)}
        onSubmit={(value) => editing && submitAddress(editing, value)}
      />
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

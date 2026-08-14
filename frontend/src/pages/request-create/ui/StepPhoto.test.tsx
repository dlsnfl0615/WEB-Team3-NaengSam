import { useState } from "react";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import { StepPhoto } from "./StepPhoto";
import type { RequestForm } from "./types";

const INITIAL_FORM: RequestForm = {
  pickup: "",
  pickupDetail: "",
  dropoff: "",
  dropoffDetail: "",
  pickupMeeting: "비대면",
  dropoffMeeting: "비대면",
  itemType: "서류",
  itemSize: "S",
  itemName: "서류 봉투",
  detail: "",
  requestTag: "없음",
  etc: "",
};

function TestStepPhoto() {
  const [form, setForm] = useState(INITIAL_FORM);

  return (
    <StepPhoto
      form={form}
      update={(patch) => setForm((previous) => ({ ...previous, ...patch }))}
    />
  );
}

afterEach(cleanup);

describe("StepPhoto 배송 요청사항", () => {
  it('"기타"를 선택했을 때만 직접 입력란이 활성화된다', () => {
    render(<TestStepPhoto />);

    const input = screen.getByPlaceholderText<HTMLInputElement>(
      "추가 요청사항 직접 입력",
    );
    // 초기값은 "없음" → 비활성
    expect(input.disabled).toBe(true);

    fireEvent.click(screen.getByRole("radio", { name: "도착 시 연락" }));
    expect(input.disabled).toBe(true);

    fireEvent.click(screen.getByRole("radio", { name: "파손주의" }));
    expect(input.disabled).toBe(true);

    fireEvent.click(screen.getByRole("radio", { name: "기타" }));
    expect(input.disabled).toBe(false);
  });

  it('"기타"가 아닌 태그로 옮기면 직접 입력값을 비우고 비활성화한다', () => {
    render(<TestStepPhoto />);

    const input = screen.getByPlaceholderText<HTMLInputElement>(
      "추가 요청사항 직접 입력",
    );

    fireEvent.click(screen.getByRole("radio", { name: "기타" }));
    fireEvent.change(input, { target: { value: "문 앞에 놓아주세요" } });
    expect(input.value).toBe("문 앞에 놓아주세요");

    fireEvent.click(screen.getByRole("radio", { name: "도착 시 연락" }));
    expect(input.disabled).toBe(true);
    expect(input.value).toBe("");
  });
});

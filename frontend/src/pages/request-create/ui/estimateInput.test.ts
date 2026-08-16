import { describe, expect, it } from "vitest";
import { hasEstimateInputChanged } from "./estimateInput";
import type { RequestForm } from "./types";

const FORM: RequestForm = {
  pickup: "서울시 강남구 테헤란로 123",
  pickupDetail: "101호",
  dropoff: "서울시 서초구 서초대로 45",
  dropoffDetail: "202호",
  pickupMeeting: "비대면",
  dropoffMeeting: "비대면",
  itemType: "서류",
  itemSize: "S",
  itemName: "서류봉투",
  detail: "",
  requestTag: "없음",
  etc: "",
};

describe("hasEstimateInputChanged", () => {
  it("상세주소만 바뀌면 기존 견적을 유지한다", () => {
    expect(
      hasEstimateInputChanged(FORM, {
        pickup: FORM.pickup,
        pickupDetail: "102호",
      }),
    ).toBe(false);
  });

  it("주소를 수정하지 않고 다시 확정해도 기존 견적을 유지한다", () => {
    expect(
      hasEstimateInputChanged(FORM, {
        pickup: FORM.pickup,
        pickupDetail: FORM.pickupDetail,
        pickupMeeting: FORM.pickupMeeting,
      }),
    ).toBe(false);
  });

  it.each([
    [{ pickup: "서울시 강남구 테헤란로 456" }],
    [{ dropoff: "서울시 서초구 서초대로 78" }],
    [{ itemType: "소형택배" as const }],
    [{ itemSize: "M" as const }],
  ])("견적 입력값이 실제로 바뀌면 기존 견적을 무효화한다", (patch) => {
    expect(hasEstimateInputChanged(FORM, patch)).toBe(true);
  });
});

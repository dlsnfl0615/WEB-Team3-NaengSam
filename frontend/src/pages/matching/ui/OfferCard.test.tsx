import { describe, expect, it, vi } from "vitest";
import { fireEvent, render } from "@testing-library/react";
import { OfferCard } from "./OfferCard";

const countdown = { remainingSeconds: 30, progressPercent: 100 };

function renderCard(overrides: Partial<Parameters<typeof OfferCard>[0]> = {}) {
  const onReject = vi.fn();
  const onAccept = vi.fn();
  const utils = render(
    <OfferCard
      heading="새 드리미 요청 도착!"
      dreamiId="00000000-0000-0000-0000-000000000001"
      name="드리미 '홍길동'"
      rating={4.5}
      pickupEtaMinutes={12}
      countdown={countdown}
      onReject={onReject}
      onAccept={onAccept}
      {...overrides}
    />,
  );
  return { ...utils, onReject, onAccept };
}

describe("OfferCard", () => {
  it("드리미 이름을 그대로 표시한다", () => {
    const { getByText } = renderCard({ name: "드리미 '홍길동'" });

    expect(getByText("드리미 '홍길동'")).toBeTruthy();
  });

  it("pickupEtaMinutes가 있으면 약 N분으로 표시한다", () => {
    const { getByText } = renderCard({ pickupEtaMinutes: 12 });

    expect(getByText("약 12분")).toBeTruthy();
  });

  it("pickupEtaMinutes가 null이면 픽업 시간 확인 중을 표시한다", () => {
    const { getByText } = renderCard({ pickupEtaMinutes: null });

    expect(getByText("픽업 시간 확인 중")).toBeTruthy();
  });

  it("이름 아래에 드리미 평점을 표시한다", () => {
    const { getByText } = renderCard({ rating: 4.5 });

    expect(getByText("4.5")).toBeTruthy();
  });

  it("거절 N건 UI를 렌더링하지 않는다", () => {
    const { container } = renderCard();

    expect(container.textContent).not.toMatch(/거절\s*\d+건/);
  });

  it("거절·수락 버튼이 있고 클릭 시 각 핸들러를 호출한다", () => {
    const { getByText, onReject, onAccept } = renderCard();

    fireEvent.click(getByText("거절"));
    fireEvent.click(getByText("수락하기"));

    expect(onReject).toHaveBeenCalledTimes(1);
    expect(onAccept).toHaveBeenCalledTimes(1);
  });
});

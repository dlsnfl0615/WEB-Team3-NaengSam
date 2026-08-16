import { describe, expect, it, vi } from "vitest";
import { render } from "@testing-library/react";
import { CallCard } from "./CallCard";

const countdown = { remainingSeconds: 30, progressPercent: 100 };

describe("CallCard", () => {
  it("픽업 후 배송 라벨과 eta 값을 함께 표시한다", () => {
    const { getByText } = render(
      <CallCard
        offerId="offer-1"
        code="#B-882"
        price="₩3,000"
        place="물품 배송"
        route="출발지 → 도착지"
        deliveryDistance="1.2km"
        eta="15분"
        countdown={countdown}
        onReject={vi.fn()}
        onAccept={vi.fn()}
      />,
    );

    expect(getByText("픽업 후 배송")).toBeTruthy();
    expect(getByText("15분")).toBeTruthy();
  });
});

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

  it("pickupDistance가 있으면 픽업 거리 항목을 표시한다", () => {
    const { getByText } = render(
      <CallCard
        offerId="offer-1"
        code="#B-882"
        price="₩3,000"
        place="물품 배송"
        route="출발지 → 도착지"
        deliveryDistance="1.2km"
        eta="15분"
        pickupDistance="800m"
        countdown={countdown}
        onReject={vi.fn()}
        onAccept={vi.fn()}
      />,
    );

    expect(getByText("픽업 거리")).toBeTruthy();
    expect(getByText("800m")).toBeTruthy();
  });

  it("pickupDistance가 없으면 픽업 거리 항목을 숨긴다", () => {
    const { queryByText } = render(
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

    expect(queryByText("픽업 거리")).toBeNull();
  });

  it("expandedScopeNotice가 있으면 확장 안내 문구를 표시한다", () => {
    const { getByText } = render(
      <CallCard
        offerId="offer-1"
        code="#B-882"
        price="₩3,000"
        place="물품 배송"
        route="출발지 → 도착지"
        deliveryDistance="1.2km"
        eta="15분"
        expandedScopeNotice="대기 시간이 길어져 반경 6.0km까지 넓혀 찾은 콜이에요."
        countdown={countdown}
        onReject={vi.fn()}
        onAccept={vi.fn()}
      />,
    );

    expect(
      getByText("대기 시간이 길어져 반경 6.0km까지 넓혀 찾은 콜이에요."),
    ).toBeTruthy();
  });

  it("expandedScopeNotice가 없으면 확장 안내 문구를 표시하지 않는다", () => {
    const { queryByText } = render(
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

    expect(queryByText(/넓혀 찾은 콜이에요/)).toBeNull();
  });
});

import { afterEach, describe, expect, it } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { OfferCountdownBar } from "./OfferCountdownBar";

afterEach(() => {
  cleanup();
});

describe("OfferCountdownBar", () => {
  it("평시(5초 초과)에는 남은 초를 일반 색으로 보여준다", () => {
    render(<OfferCountdownBar remainingSeconds={20} progressPercent={66} />);

    const label = screen.getByText("20초 남음");
    expect(label.className).not.toContain("text-status-danger");
    expect(screen.getByRole("progressbar").getAttribute("aria-valuenow")).toBe("66");
  });

  it("5초 이하면 문구와 바를 위험 색(red)으로 바꾼다", () => {
    render(<OfferCountdownBar remainingSeconds={5} progressPercent={16} />);

    expect(screen.getByText("5초 남음").className).toContain("text-status-danger");
    const bar = screen.getByRole("progressbar").firstElementChild;
    expect(bar?.className).toContain("bg-status-danger");
  });

  it("0초면 만료 문구로 바뀐다", () => {
    render(<OfferCountdownBar remainingSeconds={0} progressPercent={0} />);

    expect(screen.getByText("응답 시간 만료").className).toContain("text-status-danger");
    expect(screen.queryByText(/초 남음/)).toBeNull();
  });
});

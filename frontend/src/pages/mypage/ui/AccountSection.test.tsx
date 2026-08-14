import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { AccountSection } from "./AccountSection";

afterEach(cleanup);

describe("AccountSection", () => {
  it("미등록 상태에서는 계좌 등록 안내 카드를 표시하지 않는다", () => {
    render(<AccountSection registered={false} onChange={vi.fn()} />);

    expect(screen.queryByText("현금화 계좌를 등록해주세요")).toBeNull();
    expect(screen.queryByRole("button", { name: "계좌 등록하기" })).toBeNull();
  });

  it("등록된 계좌 정보는 기존대로 표시한다", () => {
    render(<AccountSection registered onChange={vi.fn()} />);

    expect(screen.getByText("국민은행")).toBeTruthy();
    expect(screen.getByRole("button", { name: "출금하기" })).toBeTruthy();
  });
});

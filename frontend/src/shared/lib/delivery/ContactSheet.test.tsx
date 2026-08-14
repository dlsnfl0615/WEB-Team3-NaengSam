import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import { ContactSheet } from "./ContactSheet";

const apiMock = vi.hoisted(() => ({
  getDeliveryContact: vi.fn(),
  sendPing: vi.fn(),
}));

vi.mock("@/shared/api", () => ({
  api: apiMock,
  isApiError: () => false,
}));

function contactOf(viewerIsDreami: boolean) {
  return {
    result: {
      counterpartName: viewerIsDreami ? "이부름" : "김드림",
      counterpartPhoneNumber: "01012345678",
      viewerIsDreami,
    },
  };
}

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("ContactSheet", () => {
  it("부르미에게는 핑 보내기 버튼을 보여준다", async () => {
    apiMock.getDeliveryContact.mockResolvedValue(contactOf(false));

    render(<ContactSheet open orderId="order-1" onClose={vi.fn()} />);

    expect(
      await screen.findByRole("button", { name: /핑 보내기/ }),
    ).toBeTruthy();
  });

  it("드리미에게는 핑 보내기 버튼을 감춘다", async () => {
    apiMock.getDeliveryContact.mockResolvedValue(contactOf(true));

    render(<ContactSheet open orderId="order-1" onClose={vi.fn()} />);

    // 상대(부르미) 정보가 그려진 뒤에도 핑 버튼은 없어야 한다.
    expect(await screen.findByText("이부름")).toBeTruthy();
    expect(screen.queryByRole("button", { name: /핑 보내기/ })).toBeNull();
  });

  it("핑 보내기를 누르면 해당 주문으로 핑을 보내고 다시 누를 수 없게 잠근다", async () => {
    apiMock.getDeliveryContact.mockResolvedValue(contactOf(false));
    apiMock.sendPing.mockResolvedValue(undefined);

    render(<ContactSheet open orderId="order-1" onClose={vi.fn()} />);
    const button = await screen.findByRole("button", { name: /핑 보내기/ });
    button.click();

    await waitFor(() =>
      expect(apiMock.sendPing).toHaveBeenCalledWith("order-1"),
    );
    const sentButton = await screen.findByRole("button", {
      name: /핑을 보냈어요/,
    });
    expect((sentButton as HTMLButtonElement).disabled).toBe(true);
  });
});

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { useMatchingStore, type AwaitingBoormi } from "@/shared/store/matchingStore";
import { useSessionStore } from "@/shared/store/sessionStore";
import { useToastStore } from "@/shared/store/toastStore";
import { ROUTES } from "@/shared/config/routes";
import { MatchingPopup } from "./MatchingPopup";

// SSE 연결은 이 테스트의 대상이 아니다(팝업이 무엇을 어떻게 덮는지만 본다).
vi.mock("@/shared/lib", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/shared/lib")>()),
  useSse: () => ({ status: "connected" }),
}));

const WAITING_TITLE = "부르미의 응답을 기다리고 있어요…";

function awaiting(): AwaitingBoormi {
  const now = Date.now();
  return {
    offerId: "offer-1",
    orderId: "order-1",
    itemName: "설계도면",
    acceptedAt: new Date(now).toISOString(),
    expiresAt: new Date(now + 30_000).toISOString(),
  };
}

function renderPopup() {
  return render(
    <MemoryRouter initialEntries={["/activity"]}>
      <MatchingPopup />
    </MemoryRouter>,
  );
}

beforeEach(() => {
  useToastStore.getState().clear();
  useSessionStore.setState({ isAuthenticated: true });
  useMatchingStore.setState({
    pendingOffer: null,
    incomingDreami: null,
    awaitingBoormi: null,
    message: null,
    submitting: false,
  });
});

afterEach(() => {
  cleanup();
  useMatchingStore.setState({ awaitingBoormi: null, message: null });
});

describe("MatchingPopup 부르미 응답 대기", () => {
  it("대기_카드에_수락한_물품명을_보여준다", () => {
    useMatchingStore.setState({ awaitingBoormi: awaiting() });

    renderPopup();

    expect(screen.getByText(WAITING_TITLE)).toBeTruthy();
    expect(screen.getByText(/설계도면/)).toBeTruthy();
  });

  it("대기_카드는_뒤_화면을_덮지_않는다", () => {
    // 대기 카드에는 누를 버튼이 없다. 뒤를 덮어버리면 사용자가 빠져나갈 방법 없이 앱 전체가
    // 먹통이 되므로(활동 목록 등 어떤 것도 눌리지 않음), 배경도 없고 클릭도 통과해야 한다.
    useMatchingStore.setState({ awaitingBoormi: awaiting() });

    const { container } = renderPopup();

    const overlay = container.firstElementChild as HTMLElement;
    expect(overlay.className).toContain("pointer-events-none");
    expect(container.querySelector('[aria-hidden="true"].absolute')).toBeNull();
  });

  it("부르미의_드리미_요청_팝업은_그대로_보이고_뒤_화면을_덮는다", () => {
    useMatchingStore.setState({
      incomingDreami: {
        offerId: "offer-1",
        orderId: "order-1",
        dreamiId: "dreami-1",
        pickupEtaMinutes: 7,
        acceptedAt: new Date().toISOString(),
        expiresAt: new Date(Date.now() + 30_000).toISOString(),
        profile: { name: "핀", dreamiAvgScore: 4.8 },
      },
    });

    const { container } = renderPopup();

    expect(screen.getByText("새 드리미 요청 도착!")).toBeTruthy();
    expect(screen.getByText("드리미 '핀'")).toBeTruthy();
    expect(screen.getByRole("button", { name: "수락하기" })).toBeTruthy();
    const overlay = container.firstElementChild as HTMLElement;
    expect(overlay.className).not.toContain("pointer-events-none");
    expect(container.querySelector('[aria-hidden="true"].absolute')).toBeTruthy();
  });

  it("응답이_필요한_콜_카드는_기존대로_뒤_화면을_덮는다", () => {
    useMatchingStore.setState({
      pendingOffer: {
        offerId: "offer-1", orderId: "order-1", deliveryAmount: 3000, itemName: "설계도면",
        deliveryEta: 10, deliveryDistance: 800, originLatitude: null, originLongitude: null,
        originAlias: null, originAddressLine1: null, destinationLatitude: null,
        destinationLongitude: null, destinationAlias: null, destinationAddressLine1: null,
        deliveryRequest: null,
        acceptedAt: undefined,
        offeredAt: new Date().toISOString(),
        expiresAt: new Date(Date.now() + 30_000).toISOString(),
      } as never,
    });

    const { container } = renderPopup();

    const overlay = container.firstElementChild as HTMLElement;
    expect(overlay.className).not.toContain("pointer-events-none");
    expect(container.querySelector('[aria-hidden="true"].absolute')).toBeTruthy();
  });
});

describe("MatchingPopup 매칭 안내", () => {
  it("안내_메시지는_상단_토스트_스택으로_올린다", () => {
    useMatchingStore.setState({ message: "부르미가 요청을 거절했어요." });

    const { container } = renderPopup();

    // 하단 오버레이는 카드가 있을 때만 그린다(안내는 ToastViewport가 상단에서 내려주며 표시).
    expect(container.firstElementChild).toBeNull();
    expect(
      useToastStore
        .getState()
        .toasts.map((toast) => [toast.title, toast.persistent]),
    ).toContainEqual(["부르미가 요청을 거절했어요.", true]);
  });

  it("토스트를_닫으면_안내_메시지도_비운다", () => {
    useMatchingStore.setState({ message: "부르미가 요청을 거절했어요." });
    renderPopup();
    const toastId = useToastStore.getState().toasts[0].id;

    useToastStore.getState().dismiss(toastId);

    // 메시지가 남아 있으면 같은 안내가 다시 와도 새 토스트가 뜨지 않는다.
    expect(useMatchingStore.getState().message).toBeNull();
  });

  it("거절_사유_화면에서는_안내를_띄우지_않는다", () => {
    useMatchingStore.setState({ message: "부르미가 요청을 거절했어요." });

    render(
      <MemoryRouter initialEntries={[ROUTES.rejectReason]}>
        <MatchingPopup />
      </MemoryRouter>,
    );

    expect(useToastStore.getState().toasts).toHaveLength(0);
  });
});

import { act } from "react";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { MemoryRouter, useLocation } from "react-router-dom";
import { useMatchingStore } from "@/shared/store/matchingStore";
import { useSessionStore } from "@/shared/store/sessionStore";
import { useToastStore } from "@/shared/store/toastStore";
import { PushNavigationBridge } from "./PushNavigationBridge";

class FakeServiceWorkerContainer extends EventTarget {}

function LocationProbe() {
  return <span>{useLocation().pathname}</span>;
}

const originalServiceWorker = Object.getOwnPropertyDescriptor(
  navigator,
  "serviceWorker",
);

beforeEach(() => {
  Object.defineProperty(navigator, "serviceWorker", {
    configurable: true,
    value: new FakeServiceWorkerContainer(),
  });
  useMatchingStore.setState({
    pendingOffer: null,
    incomingDreami: null,
    syncCurrentMatching: vi.fn().mockResolvedValue(undefined),
  });
  useSessionStore.setState({ isAuthenticated: true, hydrated: true });
  useToastStore.getState().clear();
});

afterEach(() => {
  cleanup();
  useToastStore.getState().clear();
  if (originalServiceWorker) {
    Object.defineProperty(navigator, "serviceWorker", originalServiceWorker);
  } else {
    Reflect.deleteProperty(navigator, "serviceWorker");
  }
});

describe("PushNavigationBridge", () => {
  it("푸시 클릭 메시지를 받으면 SPA 이동 후 매칭 상태를 동기화한다", async () => {
    render(
      <MemoryRouter initialEntries={["/"]}>
        <PushNavigationBridge />
        <LocationProbe />
      </MemoryRouter>,
    );

    act(() => {
      navigator.serviceWorker.dispatchEvent(
        new MessageEvent("message", {
          data: { type: "PUSH_NAVIGATE", url: "/matching" },
        }),
      );
    });

    expect(screen.getByText("/matching")).toBeTruthy();
    await waitFor(() => {
      expect(
        useMatchingStore.getState().syncCurrentMatching,
      ).toHaveBeenCalledOnce();
    });
  });

  it("동기화 후 매칭 상태가 비어 있으면 마감 안내를 중복 없이 표시한다", async () => {
    render(
      <MemoryRouter>
        <PushNavigationBridge />
      </MemoryRouter>,
    );

    act(() => {
      navigator.serviceWorker.dispatchEvent(
        new MessageEvent("message", {
          data: { type: "PUSH_NAVIGATE", url: "/matching" },
        }),
      );
    });

    await waitFor(() => {
      expect(useToastStore.getState().toasts).toEqual([
        expect.objectContaining({
          title: "요청이 이미 마감됐어요",
          dedupeKey: "offer-expired",
        }),
      ]);
    });
  });

  it("배달 알림은 매칭 상태가 비어 있어도 마감 안내를 표시하지 않는다", async () => {
    render(
      <MemoryRouter>
        <PushNavigationBridge />
      </MemoryRouter>,
    );

    act(() => {
      navigator.serviceWorker.dispatchEvent(
        new MessageEvent("message", {
          data: {
            type: "PUSH_NAVIGATE",
            url: "/delivery-detail?orderId=order-id",
          },
        }),
      );
    });

    await waitFor(() => {
      expect(
        useMatchingStore.getState().syncCurrentMatching,
      ).toHaveBeenCalledOnce();
    });
    expect(useToastStore.getState().toasts).toHaveLength(0);
  });

  it("앱이 다시 보이면 매칭 상태를 동기화한다", () => {
    render(
      <MemoryRouter>
        <PushNavigationBridge />
      </MemoryRouter>,
    );
    Object.defineProperty(document, "visibilityState", {
      configurable: true,
      value: "visible",
    });

    act(() => {
      document.dispatchEvent(new Event("visibilitychange"));
    });

    expect(
      useMatchingStore.getState().syncCurrentMatching,
    ).toHaveBeenCalledOnce();
  });

  it("로그아웃 상태로 다시 보여도 보호 API를 호출하지 않는다", () => {
    useSessionStore.setState({ isAuthenticated: false });
    render(
      <MemoryRouter>
        <PushNavigationBridge />
      </MemoryRouter>,
    );
    Object.defineProperty(document, "visibilityState", {
      configurable: true,
      value: "visible",
    });

    act(() => {
      document.dispatchEvent(new Event("visibilitychange"));
    });

    expect(
      useMatchingStore.getState().syncCurrentMatching,
    ).not.toHaveBeenCalled();
  });
});

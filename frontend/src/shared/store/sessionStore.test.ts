import { beforeEach, describe, expect, it, vi } from "vitest";
import { api } from "@/shared/api";
import type { LoginResultDto } from "@/shared/api";
import { useSessionStore } from "./sessionStore";

vi.mock("@/shared/api", () => ({
  api: {
    login: vi.fn(),
    pollLoginQueue: vi.fn(),
    me: vi.fn(),
  },
  SESSION_PROBE_HEADER: "X-Session-Probe",
  isApiError: () => false,
}));

const login = vi.mocked(api.login);
const pollLoginQueue = vi.mocked(api.pollLoginQueue);
const me = vi.mocked(api.me);

const TICKET_STORAGE_KEY = "naengsam.loginTicket";

/** CommonResponse 봉투. */
function envelope(result: LoginResultDto) {
  return { isSuccess: true, code: "COM200", message: "ok", result };
}

/** 대기 중 응답. pollAfterMs를 1ms로 둬 테스트가 실제 대기 시간을 쓰지 않게 한다. */
function waiting(position: number) {
  return envelope({
    status: "WAITING",
    position,
    totalWaiting: 10,
    estimatedWaitSeconds: position,
    pollAfterMs: 1,
  });
}

/** 차례가 됐을 때의 응답(이 응답에서 세션 쿠키가 발급된다). */
const success = envelope({ status: "SUCCESS" });

/** 새로고침 직전까지 저장돼 있던 티켓. */
function storeTicket(ticketId: string, position: number) {
  sessionStorage.setItem(
    TICKET_STORAGE_KEY,
    JSON.stringify({
      ticketId,
      queue: {
        position,
        totalWaiting: 10,
        estimatedWaitSeconds: position,
        initialPosition: 8,
      },
    }),
  );
}

beforeEach(() => {
  sessionStorage.clear();
  login.mockReset();
  pollLoginQueue.mockReset();
  me.mockReset();
  me.mockResolvedValue({
    result: { boormiId: "b-1", name: "부르미" },
  } as never);
  useSessionStore.setState({
    user: null,
    isAuthenticated: false,
    hydrated: false,
    loginQueue: null,
  });
});

describe("sessionStore 대기열 재개", () => {
  it("저장된 티켓이 없으면 아무 요청도 하지 않고 null을 반환한다", async () => {
    const user = await useSessionStore.getState().resumeQueuedLogin();

    expect(user).toBeNull();
    expect(pollLoginQueue).not.toHaveBeenCalled();
    expect(me).not.toHaveBeenCalled();
  });

  it("저장된 티켓으로 폴링을 이어가고 차례가 되면 로그인된다", async () => {
    storeTicket("ticket-1", 3);
    pollLoginQueue
      .mockResolvedValueOnce(waiting(2) as never)
      .mockResolvedValueOnce(success as never);

    const user = await useSessionStore.getState().resumeQueuedLogin();

    expect(pollLoginQueue).toHaveBeenCalledWith("ticket-1");
    expect(user?.id).toBe("b-1");
    expect(useSessionStore.getState().isAuthenticated).toBe(true);
    expect(useSessionStore.getState().loginQueue).toBeNull();
    expect(sessionStorage.getItem(TICKET_STORAGE_KEY)).toBeNull();
  });

  it("진행률의 분모는 저장된 최초 순번을 그대로 쓴다", async () => {
    storeTicket("ticket-1", 3);
    let shown: import("./sessionStore").LoginQueueState | null = null;
    pollLoginQueue
      .mockResolvedValueOnce(waiting(2) as never)
      // 첫 폴링 응답(순번 2)이 화면에 반영된 뒤의 상태를 본다.
      .mockImplementationOnce(async () => {
        shown = useSessionStore.getState().loginQueue;
        return success as never;
      });

    await useSessionStore.getState().resumeQueuedLogin();

    expect(shown).toEqual({
      position: 2,
      totalWaiting: 10,
      estimatedWaitSeconds: 2,
      // 분모는 새로 받은 순번이 아니라 저장된 최초 순번이어야 진행률 바가 되감기지 않는다.
      initialPosition: 8,
    });
  });

  it("만료된 티켓이면 예외가 나고 저장된 티켓이 지워진다", async () => {
    storeTicket("ticket-1", 3);
    pollLoginQueue.mockRejectedValue(new Error("expired"));

    await expect(
      useSessionStore.getState().resumeQueuedLogin(),
    ).rejects.toThrow();

    expect(sessionStorage.getItem(TICKET_STORAGE_KEY)).toBeNull();
    expect(useSessionStore.getState().loginQueue).toBeNull();
    expect(useSessionStore.getState().isAuthenticated).toBe(false);
  });
});

describe("sessionStore 로그인", () => {
  it("대기 티켓을 받으면 폴링 전에 저장하고 끝나면 지운다", async () => {
    login.mockResolvedValue(
      envelope({
        status: "QUEUED",
        ticketId: "ticket-9",
        position: 5,
        totalWaiting: 12,
        estimatedWaitSeconds: 5,
        pollAfterMs: 1,
      }) as never,
    );
    let storedDuringPoll: string | null = null;
    pollLoginQueue.mockImplementation(async () => {
      storedDuringPoll = sessionStorage.getItem(TICKET_STORAGE_KEY);
      return success as never;
    });

    await useSessionStore
      .getState()
      .login({ email: "a@test.com", password: "test1234!" });

    expect(storedDuringPoll).toContain("ticket-9");
    expect(sessionStorage.getItem(TICKET_STORAGE_KEY)).toBeNull();
  });
});

import { create } from "zustand";
import { api, SESSION_PROBE_HEADER } from "@/shared/api";
import type { LoginResultDto, UserDto } from "@/shared/api";
import { unregisterPushSubscription } from "@/shared/lib/push/unregisterPushSubscription";
import type {
  AuthUser,
  LoginRequest,
  SignupRequest,
} from "@/shared/mock/types";

/** 로그인이 대기열에 걸렸을 때 화면에 보여줄 상태. 대기 중이 아니면 null. */
export interface LoginQueueState {
  /** 남은 순번(1이면 다음 차례). */
  position: number;
  totalWaiting: number;
  estimatedWaitSeconds: number;
  /** 처음 받은 순번. 진행률의 분모라 대기 중에는 바뀌지 않는다. */
  initialPosition: number;
}

/** 서버가 pollAfterMs를 안 줬을 때 쓰는 간격. */
const DEFAULT_POLL_MS = 1000;

/**
 * 대기 티켓 보관 키. 새로고침해도 줄을 잃지 않으려면 티켓이 페이지 수명보다 오래 남아야 한다.
 * 탭을 닫으면 사라지도록 sessionStorage를 쓴다 — 닫은 탭의 줄을 나중에 되살릴 이유가 없다.
 */
const TICKET_STORAGE_KEY = "naengsam.loginTicket";

interface StoredTicket {
  ticketId: string;
  /** 마지막으로 본 대기 상태. 새로고침 직후 첫 폴링 응답 전에도 모달을 그대로 그리기 위해 통째로 저장한다. */
  queue: LoginQueueState;
}

function readStoredTicket(): StoredTicket | null {
  const raw = sessionStorage.getItem(TICKET_STORAGE_KEY);
  if (!raw) return null;
  try {
    return JSON.parse(raw) as StoredTicket;
  } catch {
    sessionStorage.removeItem(TICKET_STORAGE_KEY);
    return null;
  }
}

function writeStoredTicket(ticketId: string, queue: LoginQueueState) {
  sessionStorage.setItem(
    TICKET_STORAGE_KEY,
    JSON.stringify({ ticketId, queue } satisfies StoredTicket),
  );
}

function clearStoredTicket() {
  sessionStorage.removeItem(TICKET_STORAGE_KEY);
}

interface SessionState {
  user: AuthUser | null;
  isAuthenticated: boolean;
  /** 앱 시작 시 세션 확인(bootstrap)이 끝났는지. 라우트 가드가 이 값을 기다린다. */
  hydrated: boolean;
  /** 대기열에 걸린 로그인의 순번·예상 대기 시간. 대기 중이 아니면 null. */
  loginQueue: LoginQueueState | null;
  /** 쿠키 세션으로 /me를 조회해 로그인 상태를 복원한다(앱 시작 1회). */
  bootstrap: () => Promise<void>;
  /** /me를 다시 조회해 activeRole·activeOrderId를 최신화한다(역할 토글 잠금 판정용). */
  refreshUser: () => Promise<void>;
  login: (dto: LoginRequest) => Promise<AuthUser>;
  signup: (dto: SignupRequest) => Promise<AuthUser>;
  /**
   * 새로고침으로 끊긴 대기열 폴링을 이어간다(저장된 티켓이 없으면 null).
   * 차례가 되면 로그인까지 끝내고 사용자를 돌려준다. 티켓 만료·로그인 실패는 예외로 던져진다.
   */
  resumeQueuedLogin: () => Promise<AuthUser | null>;
  /** 로그인 유저의 본인인증(드리미 등록). 업로드 확인 실패는 예외로 던져진다(호출부에서 처리), 미로그인 시엔 null. */
  verify: (
    idCardKey: string,
    criminalRecordKey: string,
  ) => Promise<AuthUser | null>;
  logout: () => Promise<void>;
}

/** 백엔드 UserDto → 화면용 AuthUser 매핑. */
function toAuthUser(dto: UserDto): AuthUser {
  return {
    id: dto.boormiId ?? "",
    name: dto.name ?? "",
    email: dto.email ?? "",
    // UserDto엔 역할 목록 대신 isDreami 플래그만 온다. 부르미는 항상 보유, 드리미는 등록 시 추가.
    roles: dto.isDreami ? ["부르미", "드리미"] : ["부르미"],
    activeRole: dto.activeRole,
    activeOrderId: dto.activeOrderId,
    activeOrderCd: dto.activeOrderCd,
    // 드리미 평점은 승인된 드리미일 때만 내려온다(미등록·미승인이면 null → 마이페이지에서 숨김).
    boormiRating: dto.boormiAvgScore ?? 0,
    dreamiRating: dto.dreamiAvgScore ?? undefined,
    // 신청 이력이 없으면 백엔드가 필드 자체를 내려주지 않는다(null).
    dreamiStatus: dto.dreamiStatus ?? undefined,
  };
}

/**
 * 차례가 올 때까지 대기 티켓을 폴링한다. 다음 호출까지의 간격은 서버가 준 `pollAfterMs`를 그대로 쓰며,
 * 순번이 앞당겨질수록 짧아진다. 차례가 되면 그 폴링 응답에서 세션이 만들어지므로 여기서는 반환만 한다.
 *
 * 비밀번호가 틀렸거나 티켓이 만료되면 폴링 응답이 4xx로 떨어져 예외로 빠져나간다.
 * 화면을 벗어나도 루프를 따로 끊지 않는다 — 티켓 수명(2분)이 지나면 만료 응답으로 스스로 끝난다.
 *
 * `ticketId`와 `initialPosition`을 따로 받는 이유: 재개 경로가 넘기는 폴링 응답(WAITING)에는 ticketId가 없고,
 * 진행률의 분모는 새로 받은 순번이 아니라 처음 줄을 섰을 때의 순번이어야 바가 되감기지 않는다.
 */
async function waitInQueue(
  ticketId: string,
  first: LoginResultDto,
  initialPosition: number,
  onProgress: (state: LoginQueueState) => void,
): Promise<void> {
  let current = first;

  while (current.status === "QUEUED" || current.status === "WAITING") {
    const queue: LoginQueueState = {
      position: current.position ?? 1,
      totalWaiting: current.totalWaiting ?? 1,
      estimatedWaitSeconds: current.estimatedWaitSeconds ?? 1,
      initialPosition,
    };
    // 새로고침 후 재개가 최신 순번에서 이어지도록 매 폴링마다 갱신한다.
    writeStoredTicket(ticketId, queue);
    onProgress(queue);

    await new Promise((resolve) =>
      setTimeout(resolve, current.pollAfterMs ?? DEFAULT_POLL_MS),
    );
    const { result } = await api.pollLoginQueue(ticketId);
    current = result ?? {};
  }
}

/** 회원가입 폼의 생년월일("YYYY.M.D") → 백엔드 ISO 날짜("YYYY-MM-DD"). */
function toIsoDate(birth: string): string {
  const [year, month, day] = birth.split(".");
  return `${year}-${month.padStart(2, "0")}-${day.padStart(2, "0")}`;
}

/** 재개 폴링이 이미 돌고 있는지. 스토어 상태로 두면 첫 진입 시점에는 아직 비어 있어 가드가 안 된다. */
let resuming = false;

/**
 * 로그인 세션(유저·역할)을 화면 간 공유하는 전역 스토어(#37 인증).
 * 실제 백엔드 API(`@/shared/api`)를 호출한다. 인증은 세션 쿠키(JSESSIONID) 기반.
 */
export const useSessionStore = create<SessionState>((set, get) => ({
  user: null,
  isAuthenticated: false,
  hydrated: false,
  // 새로고침 직후 첫 렌더부터 대기 모달이 그대로 떠 있도록 저장된 상태로 시작한다(로그인 폼 번쩍임 방지).
  loginQueue: readStoredTicket()?.queue ?? null,
  bootstrap: async () => {
    // probe 헤더를 실어 401이어도 전역 로그인 리다이렉트를 유발하지 않게 한다(공개 페이지 보호).
    try {
      const { result } = await api.me({
        headers: { [SESSION_PROBE_HEADER]: "1" },
      });
      // 부트스트랩 도중 사용자가 먼저 로그인했다면(hydrated) 그 결과를 덮어쓰지 않는다.
      if (!get().hydrated) {
        set({ user: toAuthUser(result ?? {}), isAuthenticated: true });
      }
    } catch {
      if (!get().hydrated) {
        set({ user: null, isAuthenticated: false });
      }
    } finally {
      set({ hydrated: true });
    }
  },
  refreshUser: async () => {
    // 로그인 상태에서만 의미가 있다. 실패는 조용히 무시한다 — 역할 토글은 서버가 전환 요청 때 다시 검사한다.
    if (!get().isAuthenticated) return;
    try {
      const { result } = await api.me();
      set({ user: toAuthUser(result ?? {}) });
    } catch {
      // 세션 만료는 인터셉터가 처리한다.
    }
  },
  login: async (dto) => {
    try {
      // 동시 로그인이 몰리면 세션 대신 대기 티켓이 온다. 이 경우 차례가 될 때까지 폴링한 뒤에야 세션이 생긴다.
      const { result: ticket } = await api.login({
        email: dto.email,
        password: dto.password,
      });
      if (ticket?.status === "QUEUED" && ticket.ticketId) {
        await waitInQueue(
          ticket.ticketId,
          ticket,
          Math.max(ticket.position ?? 1, 1),
          (loginQueue) => set({ loginQueue }),
        );
      }
      // 로그인 응답엔 사용자 정보가 없으므로(세션 쿠키만 발급), 곧바로 /me로 조회한다.
      const { result } = await api.me();
      const user = toAuthUser(result ?? {});
      set({ user, isAuthenticated: true, hydrated: true });
      return user;
    } finally {
      clearStoredTicket();
      set({ loginQueue: null });
    }
  },
  resumeQueuedLogin: async () => {
    const stored = readStoredTicket();
    // 중복 진입 차단. StrictMode는 개발에서 effect를 두 번 돌리는데, 같은 티켓으로 두 루프가 돌면
    // 하나가 결과를 소비하고 다른 하나는 만료(410)를 받아 성공한 로그인에 에러가 뜬다.
    if (!stored || resuming) return null;
    resuming = true;

    try {
      const { result } = await api.pollLoginQueue(stored.ticketId);
      await waitInQueue(
        stored.ticketId,
        result ?? {},
        stored.queue.initialPosition,
        (loginQueue) => set({ loginQueue }),
      );
      const { result: me } = await api.me();
      const user = toAuthUser(me ?? {});
      set({ user, isAuthenticated: true, hydrated: true });
      return user;
    } finally {
      resuming = false;
      clearStoredTicket();
      set({ loginQueue: null });
    }
  },
  signup: async (dto) => {
    // 가입은 세션을 만들지 않는다(@PublicApi). 성공 후 곧바로 로그인해 세션을 생성한다.
    await api.signup({
      email: dto.email,
      password: dto.password,
      name: dto.name,
      phoneNumber: dto.phone,
      birthdate: toIsoDate(dto.birth),
    });
    return get().login({ email: dto.email, password: dto.password });
  },
  verify: async (idCardKey, criminalRecordKey) => {
    // S3 업로드가 실제로 끝났는지 서버에서 확인한 뒤에만 인증 신청을 저장한다. 실패(미업로드/이미 승인 등)는 예외로 던져진다.
    const { user } = get();
    if (!user) return null;
    await api.verifyUploadedDocuments({ idCardKey, criminalRecordKey });
    // isDreami가 반영된 최신 사용자 정보로 세션을 갱신한다.
    const { result } = await api.me();
    const updated = toAuthUser(result ?? {});
    set({ user: updated });
    return updated;
  },
  logout: async () => {
    // 세션이 살아 있는 동안 이 기기의 푸시 구독을 먼저 지운다. 로그아웃 후에는 호출할 수 없고,
    // 남겨두면 로그아웃한 기기로 알림이 계속 간다(공용 기기에서 특히 문제다).
    await unregisterPushSubscription();
    try {
      await api.logout();
    } finally {
      set({ user: null, isAuthenticated: false });
    }
  },
}));

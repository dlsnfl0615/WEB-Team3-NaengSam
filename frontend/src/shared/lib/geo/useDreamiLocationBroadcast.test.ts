import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act, renderHook } from "@testing-library/react";
import { api } from "@/shared/api";
import {
  LOCATION_BROADCAST_INTERVAL_MS,
  STALE_FIX_MS,
  useDreamiLocationBroadcast,
} from "./useDreamiLocationBroadcast";

vi.mock("@/shared/api", () => ({
  api: { updateDreamiLocation: vi.fn() },
}));

const updateDreamiLocation = vi.mocked(api.updateDreamiLocation);

const ORDER_ID = "order-1";

const watchHandlers: {
  success?: PositionCallback;
  error?: PositionErrorCallback;
} = {};

const geolocation = {
  watchPosition: vi.fn(
    (success: PositionCallback, error: PositionErrorCallback) => {
      watchHandlers.success = success;
      watchHandlers.error = error;
      return 1;
    },
  ),
  clearWatch: vi.fn(),
};

// navigator는 unstub하지 않는다 — 훅 정리(clearWatch)가 테스트 종료 후에 돌기 때문에 그때도 살아 있어야 한다.
vi.stubGlobal("navigator", { geolocation });

function fix(latitude: number, longitude: number) {
  return { coords: { latitude, longitude } } as GeolocationPosition;
}

/** watchPosition 에러 객체. code 상수는 브라우저 구현과 같은 값으로 채운다. */
function geolocationError(code: number) {
  return {
    code,
    message: "denied",
    PERMISSION_DENIED: 1,
    POSITION_UNAVAILABLE: 2,
    TIMEOUT: 3,
  } as GeolocationPositionError;
}

/** 좌표 하나를 훅에 전달한다(setPosition이 돌므로 act로 감싼다). */
async function emitFix(latitude: number, longitude: number) {
  await act(async () => {
    watchHandlers.success!(fix(latitude, longitude));
  });
}

/** 시간을 흘리면서 전송 프라미스(.finally의 in-flight 해제)까지 함께 비운다. */
async function advance(ms: number) {
  await act(async () => {
    await vi.advanceTimersByTimeAsync(ms);
  });
}

/**
 * 드리미 위치 전송 훅 테스트. 핵심은 "새 좌표가 끊긴 뒤에는 전송도 멈춘다"는 것 —
 * 멈춘 좌표를 계속 보내면 서버가 정상 수신으로 오해해 부르미가 GPS 끊김 안내를 받지 못한다.
 */
describe("useDreamiLocationBroadcast", () => {
  beforeEach(() => {
    vi.useFakeTimers();
    updateDreamiLocation.mockResolvedValue({
      isSuccess: true,
      code: "COM200",
      message: "ok",
      result: {},
    } as Awaited<ReturnType<typeof api.updateDreamiLocation>>);
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.clearAllMocks();
  });

  it("좌표가_계속_갱신되는_동안에는_주기적으로_전송한다", async () => {
    renderHook(() => useDreamiLocationBroadcast(ORDER_ID));

    await emitFix(37.5, 127.0);
    expect(updateDreamiLocation).toHaveBeenCalledTimes(1);

    await emitFix(37.5001, 127.0001);
    await advance(LOCATION_BROADCAST_INTERVAL_MS);
    await emitFix(37.5002, 127.0002);
    await advance(LOCATION_BROADCAST_INTERVAL_MS);

    expect(updateDreamiLocation.mock.calls.length).toBeGreaterThanOrEqual(3);
  });

  it("권한이_거부되면_더_이상_위치를_전송하지_않는다", async () => {
    const { result } = renderHook(() =>
      useDreamiLocationBroadcast(ORDER_ID),
    );

    await emitFix(37.5, 127.0);
    expect(updateDreamiLocation).toHaveBeenCalledTimes(1);

    await act(async () => {
      watchHandlers.error!(geolocationError(1)); // PERMISSION_DENIED
    });
    expect(result.current.error).toBe("denied");
    updateDreamiLocation.mockClear();

    // 권한이 끊긴 뒤로는 stale 가드(15초)를 기다리지 않고 즉시 전송이 멈춘다.
    await advance(LOCATION_BROADCAST_INTERVAL_MS * 4);

    expect(updateDreamiLocation).not.toHaveBeenCalled();
  });

  it("새_좌표가_STALE_FIX_MS_넘게_없으면_멈춘_좌표를_재전송하지_않는다", async () => {
    const { result } = renderHook(() =>
      useDreamiLocationBroadcast(ORDER_ID),
    );

    await emitFix(37.5, 127.0);

    // 에러 콜백조차 뜨지 않고 fix만 조용히 끊긴 경우(지하·터널 등).
    await advance(STALE_FIX_MS + LOCATION_BROADCAST_INTERVAL_MS);
    expect(result.current.error).toBe("GPS 위치를 확인할 수 없어요.");
    updateDreamiLocation.mockClear();

    await advance(LOCATION_BROADCAST_INTERVAL_MS * 4);

    expect(updateDreamiLocation).not.toHaveBeenCalled();
  });

  it("좌표가_다시_들어오면_전송이_재개된다", async () => {
    renderHook(() => useDreamiLocationBroadcast(ORDER_ID));

    await emitFix(37.5, 127.0);
    await act(async () => {
      watchHandlers.error!(geolocationError(1));
    });
    await advance(LOCATION_BROADCAST_INTERVAL_MS);
    updateDreamiLocation.mockClear();

    await emitFix(37.6, 127.1); // 권한 재허용 → 새 좌표 도착
    await advance(LOCATION_BROADCAST_INTERVAL_MS);

    expect(updateDreamiLocation).toHaveBeenCalled();
  });
});

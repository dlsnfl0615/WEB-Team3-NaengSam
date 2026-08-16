import { describe, expect, it } from "vitest";
import {
  DRIVER_JITTER_RADIUS_M,
  distanceMeters,
  interpolatePoint,
  planDriverMotion,
} from "./driverMotion";

const START = { latitude: 37.5, longitude: 127 };

describe("driverMotion", () => {
  it("정지_중_작은_GPS_변화는_이동으로_보지_않는다", () => {
    const latitudeWithinJitter =
      START.latitude + (DRIVER_JITTER_RADIUS_M - 1) / 111_000;

    expect(
      planDriverMotion({
        previousTarget: START,
        previousReceivedAt: 0,
        nextTarget: { ...START, latitude: latitudeWithinJitter },
        receivedAt: 5_000,
      }),
    ).toBeNull();
  });

  it("첫_이동은_좌표_수신_간격에_맞춰_연결한다", () => {
    const nextTarget = { ...START, latitude: START.latitude + 50 / 111_000 };
    const plan = planDriverMotion({
      previousTarget: START,
      previousReceivedAt: 0,
      nextTarget,
      receivedAt: 5_000,
    });

    expect(distanceMeters(START, nextTarget)).toBeCloseTo(50, 0);
    expect(plan?.durationMs).toBeCloseTo(5_000, -1);
  });

  it("픽셀_보간은_소수점_위치를_유지한다", () => {
    expect(interpolatePoint({ x: 10, y: 20 }, { x: 11, y: 22 }, 0.25)).toEqual({
      x: 10.25,
      y: 20.5,
    });
  });
});

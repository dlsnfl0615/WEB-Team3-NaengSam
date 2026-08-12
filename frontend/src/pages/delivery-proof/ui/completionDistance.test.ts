import { describe, expect, it } from "vitest";
import { distanceMeters } from "./completionDistance";

describe("distanceMeters", () => {
  it("같은 좌표의 거리는 0m다", () => {
    const point = { latitude: 37.5665, longitude: 126.978 };

    expect(distanceMeters(point, point)).toBe(0);
  });

  it("위도 차이를 미터 단위 직선거리로 계산한다", () => {
    const distance = distanceMeters(
      { latitude: 37.5665, longitude: 126.978 },
      { latitude: 37.5683, longitude: 126.978 },
    );

    expect(distance).toBeGreaterThan(199);
    expect(distance).toBeLessThan(202);
  });
});

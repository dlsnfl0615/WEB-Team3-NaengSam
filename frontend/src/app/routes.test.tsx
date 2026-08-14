import type { ReactElement } from "react";
import { matchRoutes } from "react-router-dom";
import { describe, expect, it } from "vitest";
import { ROUTES } from "@/shared/config/routes";
import { routes } from "./routes";

describe("app routes", () => {
  it("등록되지 않은 경로를 홈으로 리다이렉트한다", () => {
    const matches = matchRoutes(routes, "/missing-page");
    const fallback = matches?.at(-1)?.route;
    const element = fallback?.element as ReactElement<{
      replace?: boolean;
      to?: string;
    }>;

    expect(fallback?.path).toBe("*");
    expect(element.props.to).toBe(ROUTES.home);
    expect(element.props.replace).toBe(true);
  });
});

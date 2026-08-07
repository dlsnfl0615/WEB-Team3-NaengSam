import type { RouteObject } from "react-router-dom";
import { ROUTES } from "@/shared/config/routes";
import { MatchingStatusScreen } from "./ui/MatchingStatusScreen";

export const route: RouteObject = {
  path: ROUTES.matchingStatus,
  element: <MatchingStatusScreen />,
};

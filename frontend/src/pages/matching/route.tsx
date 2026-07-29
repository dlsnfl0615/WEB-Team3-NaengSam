import type { RouteObject } from "react-router-dom";
import { ROUTES } from "@/shared/config/routes";
import { MatchingScreen } from "./ui/MatchingScreen";

export const route: RouteObject = {
  path: ROUTES.matching,
  element: <MatchingScreen />,
};

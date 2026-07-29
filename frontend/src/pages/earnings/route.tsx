import type { RouteObject } from "react-router-dom";
import { ROUTES } from "@/shared/config/routes";
import { EarningsScreen } from "./ui/EarningsScreen";

export const route: RouteObject = {
  path: ROUTES.earnings,
  element: <EarningsScreen />,
};

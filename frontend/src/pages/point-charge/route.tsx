import type { RouteObject } from "react-router-dom";
import { ROUTES } from "@/shared/config/routes";
import { PointChargeScreen } from "./ui/PointChargeScreen";

export const route: RouteObject = {
  path: ROUTES.pointCharge,
  element: <PointChargeScreen />,
};

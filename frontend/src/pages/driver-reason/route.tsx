import type { RouteObject } from "react-router-dom";
import { ROUTES } from "@/shared/config/routes";
import { DriverReasonScreen } from "./ui/DriverReasonScreen";

export const route: RouteObject = {
  path: ROUTES.driverReason,
  element: <DriverReasonScreen />,
};

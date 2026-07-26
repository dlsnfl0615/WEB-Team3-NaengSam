import type { RouteObject } from "react-router-dom";
import { ROUTES } from "@/shared/config/routes";
import { DriverHomeScreen } from "./ui/DriverHomeScreen";

export const route: RouteObject = {
  path: ROUTES.driverHome,
  element: <DriverHomeScreen />,
};

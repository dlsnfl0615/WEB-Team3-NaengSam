import type { RouteObject } from "react-router-dom";
import { ROUTES } from "@/shared/config/routes";
import { ActivityDetailDriverScreen } from "./ui/ActivityDetailDriverScreen";

export const route: RouteObject = {
  path: ROUTES.activityDetailDriver,
  element: <ActivityDetailDriverScreen />,
};

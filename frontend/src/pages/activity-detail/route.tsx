import type { RouteObject } from "react-router-dom";
import { ROUTES } from "@/shared/config/routes";
import { ActivityDetailScreen } from "./ui/ActivityDetailScreen";

export const route: RouteObject = {
  path: ROUTES.activityDetail,
  element: <ActivityDetailScreen />,
};

import type { RouteObject } from "react-router-dom";
import { ROUTES } from "@/shared/config/routes";
import { ActivityScreen } from "./ui/ActivityScreen";

export const route: RouteObject = {
  path: ROUTES.activity,
  element: <ActivityScreen />,
};

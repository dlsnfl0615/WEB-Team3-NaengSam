import type { RouteObject } from "react-router-dom";
import { ROUTES } from "@/shared/config/routes";
import { RequestCreateScreen } from "./ui/RequestCreateScreen";

export const route: RouteObject = {
  path: ROUTES.requestCreate,
  element: <RequestCreateScreen />,
};

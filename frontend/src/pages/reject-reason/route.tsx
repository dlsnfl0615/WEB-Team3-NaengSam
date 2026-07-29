import type { RouteObject } from "react-router-dom";
import { ROUTES } from "@/shared/config/routes";
import { RejectReasonScreen } from "./ui/RejectReasonScreen";

export const route: RouteObject = {
  path: ROUTES.rejectReason,
  element: <RejectReasonScreen />,
};

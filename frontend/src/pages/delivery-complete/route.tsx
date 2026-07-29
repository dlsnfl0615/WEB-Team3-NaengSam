import type { RouteObject } from "react-router-dom";
import { ROUTES } from "@/shared/config/routes";
import { DeliveryCompleteScreen } from "./ui/DeliveryCompleteScreen";

export const route: RouteObject = {
  path: ROUTES.deliveryComplete,
  element: <DeliveryCompleteScreen />,
};

import type { RouteObject } from "react-router-dom";
import { ROUTES } from "@/shared/config/routes";
import { DeliveryDetailScreen } from "./ui/DeliveryDetailScreen";

export const route: RouteObject = {
  path: ROUTES.deliveryDetail,
  element: <DeliveryDetailScreen />,
};

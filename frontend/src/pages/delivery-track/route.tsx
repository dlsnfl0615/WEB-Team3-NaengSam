import type { RouteObject } from "react-router-dom";
import { ROUTES } from "@/shared/config/routes";
import { DeliveryTrackScreen } from "./ui/DeliveryTrackScreen";

export const route: RouteObject = {
  path: ROUTES.deliveryTrack,
  element: <DeliveryTrackScreen />,
};

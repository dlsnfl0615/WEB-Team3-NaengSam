import type { RouteObject } from "react-router-dom";
import { ROUTES } from "@/shared/config/routes";
import { DeliveryProofScreen } from "./ui/DeliveryProofScreen";

export const route: RouteObject = {
  path: ROUTES.deliveryProof,
  element: <DeliveryProofScreen />,
};

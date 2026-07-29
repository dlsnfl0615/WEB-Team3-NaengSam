import type { RouteObject } from "react-router-dom";
import { ROUTES } from "@/shared/config/routes";
import { DestinationSearchScreen } from "./ui/DestinationSearchScreen";

export const route: RouteObject = {
  path: ROUTES.destinationSearch,
  element: <DestinationSearchScreen />,
};

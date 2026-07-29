import type { RouteObject } from "react-router-dom";
import { ROUTES } from "@/shared/config/routes";
import { WalletScreen } from "./ui/WalletScreen";

export const route: RouteObject = {
  path: ROUTES.wallet,
  element: <WalletScreen />,
};

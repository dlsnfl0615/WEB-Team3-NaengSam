import type { RouteObject } from "react-router-dom";
import { ROUTES } from "@/shared/config/routes";
import { MypageScreen } from "./ui/MypageScreen";

export const route: RouteObject = {
  path: ROUTES.mypage,
  element: <MypageScreen />,
};

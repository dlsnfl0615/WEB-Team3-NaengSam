import { useLocation, useNavigate } from "react-router-dom";
import { Icon, type IconName } from "../Icon/Icon";
import { ROUTES } from "@/shared/config/routes";
import { cn } from "@/shared/lib/cn";

export interface NavItem {
  name: IconName;
  label: string;
  /** 탭이 가리키는 경로. 현재 경로와 같으면 활성 탭이 됩니다. */
  path: string;
}

export interface BottomNavProps {
  items?: NavItem[];
}

const DEFAULT_ITEMS: NavItem[] = [
  { name: "home", label: "홈", path: ROUTES.home },
  { name: "activity", label: "활동", path: ROUTES.activity },
  { name: "point", label: "수익", path: ROUTES.wallet },
  { name: "profile", label: "마이", path: ROUTES.mypage },
];

/** 하단 탭 바. 현재 경로의 탭이 활성(teal-700)이고, 누르면 해당 화면으로 이동합니다. */
export function BottomNav({ items = DEFAULT_ITEMS }: BottomNavProps) {
  const navigate = useNavigate();
  const { pathname } = useLocation();

  return (
    <nav className="flex items-start justify-between border-t border-track pt-2.5">
      {items.map((item) => {
        const isActive = item.path === pathname;
        return (
          <button
            key={item.path}
            type="button"
            onClick={() => navigate(item.path)}
            className={cn(
              "flex flex-1 flex-col items-center gap-1",
              isActive ? "text-teal-700" : "text-muted",
            )}
            aria-current={isActive ? "page" : undefined}
          >
            <Icon name={item.name} size={24} />
            <span
              className={cn("text-xs", isActive ? "font-bold" : "font-normal")}
            >
              {item.label}
            </span>
          </button>
        );
      })}
    </nav>
  );
}

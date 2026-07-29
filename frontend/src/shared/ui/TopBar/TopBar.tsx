import { useNavigate } from "react-router-dom";
import { Icon, type IconName } from "../Icon/Icon";
import { ROUTES } from "@/shared/config/routes";

export interface TopBarProps {
  title: string;
  /** 뒤로가기 버튼 표시 */
  onBack?: () => void;
  /** 우측 액션 아이콘들(기본: bell, profile) */
  actions?: IconName[];
  onAction?: (name: IconName) => void;
}

/** 화면 상단 헤더. 제목 + (뒤로가기) + 우측 액션 아이콘. */
export function TopBar({
  title,
  onBack,
  actions = ["bell", "profile"],
  onAction,
}: TopBarProps) {
  const navigate = useNavigate();

  /** onAction이 없으면 profile 아이콘은 마이페이지로 이동합니다. */
  const handleAction = (name: IconName) => {
    if (onAction) {
      onAction(name);
      return;
    }
    if (name === "profile") navigate(ROUTES.mypage);
  };

  return (
    <header className="flex items-center gap-2">
      {onBack && (
        <button type="button" onClick={onBack} aria-label="뒤로가기">
          <Icon name="back" size={20} className="text-navy-900" />
        </button>
      )}
      <h1 className="flex-1 text-xl font-bold tracking-[-0.4px] text-navy-900">
        {title}
      </h1>
      {actions.map((name) => (
        <button
          key={name}
          type="button"
          onClick={() => handleAction(name)}
          aria-label={name}
        >
          <Icon name={name} size={20} className="text-navy-900" />
        </button>
      ))}
    </header>
  );
}

/** 마이페이지 메뉴 목록 데이터. */
export interface MenuItem {
  label: string;
  /** 값이 있으면 화살표 대신 뱃지를 노출합니다. */
  badge?: string;
  /** 로그아웃처럼 비중이 낮은 항목은 흐린 글자색으로. */
  muted?: boolean;
  /** 클릭 시 동작(예: 로그아웃). 없으면 표시만 하고 아무 동작 안 함. */
  onClick?: () => void;
}

export const ACCOUNT_MENU: MenuItem[] = [
  { label: "내 정보 수정" },
  { label: "본인인증 · 드리미 등록", badge: "완료" },
  { label: "알림 설정" },
];

export const SUPPORT_MENU: MenuItem[] = [
  { label: "고객센터 · 자주 묻는 질문" },
  { label: "로그아웃", muted: true },
];

import activity from "../../assets/icons/activity.svg?raw";
import back from "../../assets/icons/back.svg?raw";
import bank from "../../assets/icons/bank.svg?raw";
import bell from "../../assets/icons/bell.svg?raw";
import camera from "../../assets/icons/camera.svg?raw";
import card from "../../assets/icons/card.svg?raw";
import check from "../../assets/icons/check.svg?raw";
import close from "../../assets/icons/close.svg?raw";
import document from "../../assets/icons/document.svg?raw";
import drink from "../../assets/icons/drink.svg?raw";
import home from "../../assets/icons/home.svg?raw";
import more from "../../assets/icons/more.svg?raw";
import packageIcon from "../../assets/icons/package.svg?raw";
import phone from "../../assets/icons/phone.svg?raw";
import pin from "../../assets/icons/pin.svg?raw";
import point from "../../assets/icons/point.svg?raw";
import profile from "../../assets/icons/profile.svg?raw";
import search from "../../assets/icons/search.svg?raw";
import star from "../../assets/icons/star.svg?raw";
import time from "../../assets/icons/time.svg?raw";
import transfer from "../../assets/icons/transfer.svg?raw";

/**
 * 디자인 시스템 아이콘 세트(Figma icon/* 20종) 이름 → 인라인 SVG 마크업.
 * `?raw`로 SVG 원본을 주입해 stroke/fill 을 currentColor 로 재색합니다(.ds-icon CSS 참고).
 */
export const ICONS = {
  activity,
  back,
  bank,
  bell,
  camera,
  card,
  check,
  close,
  document,
  drink,
  home,
  more,
  package: packageIcon,
  phone,
  pin,
  point,
  profile,
  search,
  star,
  time,
  transfer,
} as const;

export type IconName = keyof typeof ICONS;

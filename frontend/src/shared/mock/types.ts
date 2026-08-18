import type { IconName } from "@/shared/ui";
import type { Role } from "@/shared/lib/role/RoleContext";

/** 드리미 인증 신청 이력의 상태. 신청한 적이 없으면 필드 자체가 없다(undefined). */
export type DreamiVerificationStatus =
  | "REQUESTED"
  | "REVIEWING"
  | "APPROVED"
  | "REJECTED";

/** 배달(드림) 라이프사이클 상태. deliveryStore·진행 화면 공용. */
export type DeliveryStatus =
  "요청됨" | "매칭중" | "픽업중" | "배송중" | "완료" | "취소" | "사고";

/**
 * 배달 1건. 활동 내역·수익 집계·진행 화면이 모두 이 엔티티에서 파생된다.
 * 실제 API의 배달 리소스 응답 모양에 맞춘다.
 */
export interface Delivery {
  id: string;
  /** 표시용 콜 코드(예: "#B-882"). */
  code: string;
  icon: IconName;
  /** 목록 제목(예: "음료 배송 #B-882"). */
  title: string;
  /** "서류" | "소형택배" | "음료" ... */
  itemType: string;
  itemSize: "S" | "M";
  itemName?: string;
  pickup: string;
  dropoff: string;
  /** 결제/수익 금액(원·포인트 공용, 부호 없는 정수). */
  price: number;
  status: DeliveryStatus;
  /** 이 배달에서 "나"의 역할(부르미=요청, 드리미=수행). */
  myRole: Role;
  /** 상대 드리미 이름(부르미 관점). */
  driverName?: string;
  /** 상대 부르미 이름(드리미 관점). */
  senderName?: string;
  rating?: number;
  /** 목록 표시용 시각 라벨(예: "오늘 14:20", "7/21"). */
  time: string;
  /** 부가 메모(예: "평가함", "받음"). */
  note?: string;
  eta?: string;
  distance?: string;
}

/** 로그인 사용자. authService 응답. */
export interface AuthUser {
  id: string;
  name: string;
  /** 가입/보유 역할. */
  roles: Role[];
  /** 현재 진행 중인 주문에서 맡고 있는 역할. */
  activeRole?: "BOORMI" | "DREAMI";
  /** 진행 중인 주문 식별자. 드리미가 매칭 대기만 하는 중이면 주문이 없어 비어 있다. */
  activeOrderId?: string;
  /** 진행 중인 주문의 상태. 로그인 후 복귀 화면과 토글 잠금 사유를 가르는 기준이다. */
  activeOrderCd?: string;
  /** 부르미로서 받은 평균 평점. 모든 계정이 부르미이므로 항상 있다. */
  boormiRating: number;
  /** 드리미로서 받은 평균 평점. 승인된 드리미가 아니면 없다. */
  dreamiRating?: number;
  /** 드리미 인증 신청 이력의 상태. 신청한 적이 없으면 없다. */
  dreamiStatus?: DreamiVerificationStatus;
  email: string;
}

// ── 요청 DTO (REST 바디 모양) ──

export interface LoginRequest {
  email: string;
  password: string;
}

export interface SignupRequest {
  name: string;
  birth: string;
  phone: string;
  email: string;
  password: string;
}

/** 부름 등록 요청. RequestCreate 폼에서 파생. */
export interface CreateDeliveryRequest {
  pickup: string;
  dropoff: string;
  itemType: string;
  itemSize: "S" | "M";
  itemName?: string;
  detail?: string;
  requestTag?: string;
  price: number;
}

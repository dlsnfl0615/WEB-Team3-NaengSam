import type { AuthUser, Call, Delivery, Wallet } from "./types";

/**
 * 인메모리 "서버 DB" 초기값. 새로고침 시 초기화된다(퍼시스트 없음).
 * 기존 화면 하드코딩(records.ts / history.ts / MatchingScreen)을 이관해
 * 스토어 전환 후에도 목록이 비지 않도록 한다.
 */

/** 로그인 데모용 기본 사용자. */
export const SEED_USER: AuthUser = {
  id: "u1",
  name: "김드림",
  roles: ["부르미", "드리미"],
  rating: 4.9,
  email: "kim@naengsam.app",
};

/** 배달 시드(부르미 요청분 s*, 드리미 수행분 d*). */
export const SEED_DELIVERIES: Delivery[] = [
  {
    id: "s1",
    code: "#B-901",
    icon: "document",
    title: "서류 배송",
    itemType: "서류",
    itemSize: "S",
    pickup: "A동 102호",
    dropoff: "B동 405호",
    price: 12000,
    status: "배송중",
    myRole: "부르미",
    driverName: "민",
    time: "오늘 14:20",
    note: "드리미 '민'",
    eta: "3분",
    distance: "450m",
  },
  {
    id: "s2",
    code: "#B-880",
    icon: "package",
    title: "소형택배",
    itemType: "소형택배",
    itemSize: "S",
    pickup: "C동 3F",
    dropoff: "A동 로비",
    price: 8000,
    status: "완료",
    myRole: "부르미",
    rating: 5.0,
    time: "7/21",
    note: "평가함",
  },
  {
    id: "s3",
    code: "#B-861",
    icon: "drink",
    title: "음료 배송",
    itemType: "음료",
    itemSize: "S",
    pickup: "1F 카페",
    dropoff: "12F 회의실",
    price: 4500,
    status: "완료",
    myRole: "부르미",
    driverName: "조이",
    time: "7/20",
    note: "드리미 '조이'",
  },
  {
    id: "d1",
    code: "#B-882",
    icon: "drink",
    title: "음료 배송 #B-882",
    itemType: "음료",
    itemSize: "S",
    pickup: "파르나스 24F",
    dropoff: "12F",
    price: 3500,
    status: "픽업중",
    myRole: "드리미",
    senderName: "민",
    time: "오늘 15:02",
    note: "부르미 '민'",
  },
  {
    id: "d2",
    code: "#B-771",
    icon: "document",
    title: "서류 배송",
    itemType: "서류",
    itemSize: "S",
    pickup: "B동 405호",
    dropoff: "5F 사무실",
    price: 6000,
    status: "완료",
    myRole: "드리미",
    rating: 5.0,
    time: "오늘 13:40",
    note: "받음",
  },
  {
    id: "d3",
    code: "#B-742",
    icon: "package",
    title: "소형택배",
    itemType: "소형택배",
    itemSize: "S",
    pickup: "A동 로비",
    dropoff: "C동 7F",
    price: 5000,
    status: "사고",
    myRole: "드리미",
    rating: 4.8,
    time: "오늘 11:15",
    note: "받음",
  },
];

/** 지갑 시드(포인트 잔액·머니 잔액·최근 내역). */
export const SEED_WALLET: Wallet = {
  points: 12400,
  money: 58500,
  transactions: [
    {
      id: "w1",
      icon: "point",
      title: "포인트 충전",
      detail: "7/22 · 카드결제",
      amount: 10000,
      unit: "P",
      incoming: true,
    },
    {
      id: "w2",
      icon: "document",
      title: "서류 배송 결제",
      detail: "7/21",
      amount: -2000,
      unit: "P",
      incoming: false,
    },
    {
      id: "w3",
      icon: "transfer",
      title: "머니 → 포인트 전환",
      detail: "7/20",
      amount: 5000,
      unit: "P",
      incoming: true,
    },
  ],
};

/** 드리미 대기 콜 시드(MatchingScreen 하드코딩 이관). */
export const SEED_CALLS: Call[] = [
  {
    id: "c1",
    code: "#B-882",
    price: 3500,
    place: "파르나스 타워",
    route: "24F → 12F",
    pickupDistance: "120m",
    dropoffDistance: "1.2km",
    itemType: "음료",
    icon: "drink",
  },
  {
    id: "c2",
    code: "#B-877",
    price: 5000,
    place: "삼성 SDS",
    route: "12F → 3F",
    pickupDistance: "80m",
    dropoffDistance: "600m",
    itemType: "서류",
    icon: "document",
  },
];

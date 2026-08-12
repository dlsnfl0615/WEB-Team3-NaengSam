package com.naengsam.quick.domain.matching.service;

/**
 * 픽업까지 이동 시간 추정에 쓰는 공용 계산기. 카카오 길찾기 API는 주문 생성 시 1회만 호출하므로(deliveryEta), 실시간으로 바뀌는
 * 드리미 위치 기준 픽업 시간은 직선거리에 도보 속도(약 4km/h)를 가정해 추정한다 — 호출량 폭증을 피하기 위함이다.
 */
public final class PickupEtaCalculator {

    private static final double WALK_SPEED_METERS_PER_MINUTE = 4_000.0 / 60;

    private PickupEtaCalculator() {
    }

    public static int minutesFromDistance(double distanceMeters) {
        return (int) Math.ceil(distanceMeters / WALK_SPEED_METERS_PER_MINUTE);
    }
}

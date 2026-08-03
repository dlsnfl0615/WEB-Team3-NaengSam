package com.naengsam.quick.domain.boormi.entity;

/**
 * 요금 재계산 결과(실제 거리 m, 요금 원, 예상시간 분).
 */
public record Charge(int distance, int amount, int eta) {
}
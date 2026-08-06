package com.naengsam.quick.domain.matching.model;

import java.time.LocalDateTime;
import java.util.UUID;

import com.naengsam.quick.domain.matching.dto.GeoPoint;

/**
 * 기다리고 있는 드리미 (콜 대기중인 드리미). 마찬가지로 status가 바뀌므로 가변 클래스
 */
public final class WaitingDreami {
    private final UUID dreamiId;
    private final GeoPoint location;
    // 엔진 스레드(단일 기록자)가 쓰고 호출 스레드(다중 판독자)가 동기화 없이 읽으므로 volatile로 가시성을 보장한다.
    private volatile WaitingDreamiStatus status;
    private volatile LocalDateTime updatedAt;

    public WaitingDreami(UUID dreamiId, GeoPoint location,
                         WaitingDreamiStatus status, LocalDateTime updatedAt) {
        this.dreamiId = dreamiId;
        this.location = location;
        this.status = status;
        this.updatedAt = updatedAt;
    }

    public UUID dreamiId() {
        return dreamiId;
    }

    public GeoPoint location() {
        return location;
    }

    public WaitingDreamiStatus status() {
        return status;
    }

    public LocalDateTime updatedAt() {
        return updatedAt;
    }

    public void markProposed() {
        requireStatus(WaitingDreamiStatus.MATCHING);
        this.status = WaitingDreamiStatus.PROPOSED;
        this.updatedAt = LocalDateTime.now();
    }

    public void markMatching() {
        this.status = WaitingDreamiStatus.MATCHING;
        this.updatedAt = LocalDateTime.now();
    }

    private void requireStatus(WaitingDreamiStatus expected) {
        if (this.status != expected) {
            throw new IllegalStateException(
                    "잘못된 상태 전이입니다: dreamiId=" + dreamiId + ", 현재상태=" + status + ", 기대상태=" + expected);
        }
    }
}

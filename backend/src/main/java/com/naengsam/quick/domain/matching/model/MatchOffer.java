package com.naengsam.quick.domain.matching.model;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * status가 계속 바뀌므로 record가 아닌 가변 클래스로 변경. (record로 유지하려면 withStatus()로 새 인스턴스를 만들어 맵에 다시 넣어야 함)
 */
public final class MatchOffer {
    private final UUID offerId;
    private final UUID orderId;
    private final UUID dreamiId;
    // 엔진 스레드(단일 기록자)가 쓰고 호출 스레드(다중 판독자)가 동기화 없이 읽으므로 volatile로 가시성을 보장한다.
    private volatile MatchOfferStatus status;
    // 마지막 상태 전이(또는 생성) 시각. PreviousOfferInteraction.occurredAt을 만드는 데 쓰이므로, 항상 호출자가
    // (엔진의 Clock으로) 넘겨준 시각을 그대로 저장한다 — 내부에서 LocalDateTime.now()를 부르지 않는다.
    private volatile LocalDateTime statusUpdatedAt;

    public MatchOffer(UUID offerId, UUID orderId, UUID dreamiId, MatchOfferStatus status, LocalDateTime createdAt) {
        this.offerId = offerId;
        this.orderId = orderId;
        this.dreamiId = dreamiId;
        this.status = status;
        requireNonNullOccurredAt(createdAt);
        this.statusUpdatedAt = createdAt;
    }

    public UUID offerId() {
        return offerId;
    }

    public UUID orderId() {
        return orderId;
    }

    public UUID dreamiId() {
        return dreamiId;
    }

    public MatchOfferStatus status() {
        return status;
    }

    public LocalDateTime statusUpdatedAt() {
        return statusUpdatedAt;
    }

    public void acceptByDreami(LocalDateTime occurredAt) {
        transition(MatchOfferStatus.OFFERED, MatchOfferStatus.PENDING_BOORMI_CONFIRMATION, occurredAt);
    }

    public void rejectByDreami(LocalDateTime occurredAt) {
        transition(MatchOfferStatus.OFFERED, MatchOfferStatus.DREAMI_REJECTED, occurredAt);
    }

    public void withdraw(LocalDateTime occurredAt) {
        transition(MatchOfferStatus.OFFERED, MatchOfferStatus.WITHDRAWN, occurredAt);
    }

    public void confirmByBoormi(LocalDateTime occurredAt) {
        transition(MatchOfferStatus.PENDING_BOORMI_CONFIRMATION, MatchOfferStatus.MATCHED, occurredAt);
    }

    public void rejectByBoormi(LocalDateTime occurredAt) {
        transition(MatchOfferStatus.PENDING_BOORMI_CONFIRMATION, MatchOfferStatus.BOORMI_REJECTED, occurredAt);
    }

    public void expireByDreami(LocalDateTime occurredAt) {
        transition(MatchOfferStatus.OFFERED, MatchOfferStatus.DREAMI_EXPIRED, occurredAt);
    }

    public void expireByBoormi(LocalDateTime occurredAt) {
        transition(MatchOfferStatus.PENDING_BOORMI_CONFIRMATION, MatchOfferStatus.BOORMI_EXPIRED, occurredAt);
    }

    private void transition(MatchOfferStatus expected, MatchOfferStatus next, LocalDateTime occurredAt) {
        requireNonNullOccurredAt(occurredAt);
        requireStatus(expected);
        this.status = next;
        this.statusUpdatedAt = occurredAt;
    }

    private void requireStatus(MatchOfferStatus expected) {
        if (this.status != expected) {
            throw new IllegalStateException(
                    "잘못된 상태 전이입니다: offerId=" + offerId + ", 현재상태=" + status + ", 기대상태=" + expected);
        }
    }

    private void requireNonNullOccurredAt(LocalDateTime occurredAt) {
        if (occurredAt == null) {
            throw new IllegalArgumentException("occurredAt은 null일 수 없습니다: offerId=" + offerId);
        }
    }
}

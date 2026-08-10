package com.naengsam.quick.domain.matching.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import com.naengsam.quick.domain.matching.dto.GeoPoint;

/**
 * 한 주문에 대해 동시에 뿌린 제안 묶음("방"). 방 자체의 상태(OPEN/MATCHED/CLOSED)와 재매칭 필요 여부를 여기서 관리한다.
 */
public final class OrderOfferGroup {
    private final UUID orderId;
    private final UUID boormiId;
    private final GeoPoint location;
    private final List<MatchOffer> offers;
    // 최초 매칭 시작 시각. 재매칭(closeForRematch → addOffersAndOpen)이 반복돼도 이 값은 바뀌지 않는다 —
    // orderWaitingTime은 "최초 매칭 요청 이후의 전체 시간"으로 정의되므로 raw candidate 생성 시점마다 다시
    // 초기화하면 안 된다.
    private final LocalDateTime matchingStartedAt;
    // 엔진 스레드(단일 기록자)가 쓰고 호출 스레드(다중 판독자)가 동기화 없이 읽으므로 volatile로 가시성을 보장한다.
    private volatile OrderOfferGroupStatus status;
    private volatile boolean rematchRequired;

    public OrderOfferGroup(UUID orderId, UUID boormiId, GeoPoint location, List<MatchOffer> offers,
            LocalDateTime matchingStartedAt) {
        if (matchingStartedAt == null) {
            throw new IllegalArgumentException("matchingStartedAt은 null일 수 없습니다: orderId=" + orderId);
        }
        this.orderId = orderId;
        this.boormiId = boormiId;
        this.location = location;
        // 라운드마다 엔진 스레드가 append하는 동시에 다른 스레드가 offers()로 읽으므로,
        // ArrayList가 아닌 CopyOnWriteArrayList로 보관해 순회/복사 중 경합을 피한다.
        this.offers = new CopyOnWriteArrayList<>(offers);
        this.matchingStartedAt = matchingStartedAt;
        this.status = OrderOfferGroupStatus.OPEN;
        this.rematchRequired = false;
    }

    public UUID orderId() {
        return orderId;
    }

    public UUID boormiId() {
        return boormiId;
    }

    public GeoPoint location() {
        return location;
    }

    public LocalDateTime matchingStartedAt() {
        return matchingStartedAt;
    }

    public List<MatchOffer> offers() {
        return List.copyOf(offers);
    }

    public OrderOfferGroupStatus status() {
        return status;
    }

    public boolean rematchRequired() {
        return rematchRequired;
    }

    /**
     * 새 오퍼 라운드를 추가하며 방을 다시 진행중(OPEN) 상태로 되돌린다. 재매칭/최초 오퍼 모두 이 경로를 사용한다.
     */
    public void addOffersAndOpen(List<MatchOffer> newOffers) {
        this.offers.addAll(newOffers);
        this.status = OrderOfferGroupStatus.OPEN;
        this.rematchRequired = false;
    }

    public void closeForRematch() {
        this.status = OrderOfferGroupStatus.CLOSED;
        this.rematchRequired = true;
    }

    /**
     * 부르미가 직접 주문을 취소한 경우. 재매칭 대상이 아니므로 rematchRequired는 세우지 않는다.
     */
    public void cancel() {
        this.status = OrderOfferGroupStatus.CLOSED;
        this.rematchRequired = false;
    }

    private void requireStatus(OrderOfferGroupStatus expected) {
        if (this.status != expected) {
            throw new IllegalStateException(
                    "잘못된 상태 전이입니다: orderId=" + orderId + ", 현재상태=" + status + ", 기대상태=" + expected);
        }
    }
}

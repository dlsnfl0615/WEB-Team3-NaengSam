package com.naengsam.quick.domain.matching.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import com.naengsam.quick.domain.matching.dto.GeoPoint;

/**
 * 한 주문에 대해 동시에 뿌린 제안 묶음("방"). 방 자체의 상태(WAITING/OPEN/MATCHED/CANCELLED)를 여기서 관리한다.
 * WAITING은 micro-batch 다음 라운드를 기다리는 중(최초 매칭 시작 직후 또는 현재 라운드가 소진된 뒤)이고, OPEN은 오퍼가
 * 나가 응답을 기다리는 중이다. WAITING/OPEN 모두 아직 진행 중인("활성") 주문으로 취급한다({@link #isActive()}).
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
        this.status = OrderOfferGroupStatus.WAITING;
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

    /**
     * WAITING/OPEN 모두 아직 진행 중인 주문이다. 취소 가능 여부, 중복 매칭 시작 방지 등에 함께 쓰인다.
     */
    public boolean isActive() {
        return status == OrderOfferGroupStatus.WAITING || status == OrderOfferGroupStatus.OPEN;
    }

    /**
     * 재매칭이 필요한지 여부. status가 WAITING이라는 것과 동치이므로 별도 필드로 관리하지 않고 계산한다.
     * (기존 API 호환을 위해 메서드는 유지)
     */
    public boolean rematchRequired() {
        return status == OrderOfferGroupStatus.WAITING;
    }

    /**
     * 새 오퍼 라운드를 추가하며 방을 다시 진행중(OPEN) 상태로 되돌린다. 재매칭/최초 오퍼 모두 이 경로를 사용한다.
     */
    public void addOffersAndOpen(List<MatchOffer> newOffers) {
        this.offers.addAll(newOffers);
        this.status = OrderOfferGroupStatus.OPEN;
    }

    /**
     * 현재 라운드의 살아 있는 오퍼가 모두 종료되어 다음 batch를 기다리는 상태로 되돌린다.
     */
    public void closeForRematch() {
        this.status = OrderOfferGroupStatus.WAITING;
    }

    /**
     * 부르미가 직접 주문을 취소한 경우. WAITING/OPEN 둘 다에서 취소할 수 있다.
     */
    public void cancel() {
        this.status = OrderOfferGroupStatus.CANCELLED;
    }

    /**
     * 드리미+부르미가 모두 수락해 매칭이 확정된 경우.
     */
    public void confirmMatch() {
        this.status = OrderOfferGroupStatus.MATCHED;
    }
}

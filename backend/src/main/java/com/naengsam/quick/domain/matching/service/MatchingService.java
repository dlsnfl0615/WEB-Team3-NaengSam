package com.naengsam.quick.domain.matching.service;

import com.naengsam.quick.domain.matching.dto.GeoPoint;
import com.naengsam.quick.domain.order.entity.Orders;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 부르미 - 드리미 매칭 로직 스켈레톤. 로직 자체는 원본 그대로 두고, 컴파일/자료구조/네이밍 일관성만 보정한 버전.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MatchingService {

    /**
     * 드리미 응답 제한시간. TODO: 정책 확정 후 조정
     */
    private static final Duration OFFER_TTL = Duration.ofSeconds(30);
    /**
     * 한 주문에 동시에 제안할 최대 드리미 수
     */
    private static final int MAX_OFFER_COUNT = 3;

    // ────────────────────────────── 도메인 타입 ──────────────────────────────
    private final Map<UUID, MatchOffer> offersById = new HashMap<>();           // Map<OfferUUID, MatchOffer>
    private final Map<UUID, Set<UUID>> offerIdsByDreamiId = new HashMap<>();    // Map<DreamiUUID, Set<OfferUUID>>

    // 하나의 주문에 대해 동시에 뿌린 제안 묶음 = "방"
    private final Map<UUID, OrderOfferGroup> orderOfferGroupsByOrderId = new HashMap<>();

    // ────────────────────────────── 저장소 ──────────────────────────────
    private final Map<UUID, WaitingDreami> dreamiMap = new HashMap<>();
    private final MatchingEngine matchingEngine;

    public List<WaitingDreami> waitingDreamis() {
        return List.copyOf(dreamiMap.values());
    }

    void alarmBySocket(String message) {
        // 소켓을 통해 알림보내기
        // 아직 코드 구현X
    }

    // 외부에서는 이 메서드로 액션을 큐에 넣기만 한다. 실제 상태 변경은 엔진 스레드에서 apply*가 수행한다.
    public void registerDreami(UUID dreamiId, GeoPoint location) {
        matchingEngine.submit(new DreamiRegister(this, dreamiId, location));
    }

    void applyRegisterDreami(UUID dreamiId, GeoPoint location) {
        dreamiMap.put(dreamiId,
                new WaitingDreami(dreamiId, location, WaitingDreamiStatus.MATCHING, LocalDateTime.now()));
        log.debug("드리미 등록 처리 완료: dreamiId={}, location={}", dreamiId, location);
    }

    public void removeDreami(UUID dreamiId) {
        matchingEngine.submit(new DreamiRemove(this, dreamiId));
    }

    void applyRemoveDreami(UUID dreamiId) {
        dreamiMap.remove(dreamiId);
        log.debug("드리미 제거 처리 완료: dreamiId={}", dreamiId);
    }

    /**
     * 매칭 시작을 요청한다. 호출 스레드에서 곧바로 확인 가능한 중복 시작만 빠르게 걸러내며, 이미 진행 중인 방이 있으면 큐에 넣지 않고 false를 반환한다. 실제 방 생성은 엔진 스레드에서 순차
     * 처리되며, 그 결과는 {@link #findOrderOfferGroup(UUID)}로 별도 조회한다.
     *
     * @param order 매칭을 시작할 주문
     * @return 매칭 시작 액션이 큐에 제출되었으면 true, 이미 진행 중인 방이 있거나 큐 제출에 실패했을 경우 false
     */
    public boolean startMatching(Orders order) {
        if (isOpenGroupExists(order.getOrderId())) {
            return false;
        }
        return matchingEngine.submit(new StartMatching(this, order));
    }

    void applyStartMatching(Orders order) {
        log.debug("매칭 시작 액션 실행: orderId={}", order.getOrderId());

        // 큐에 쌓여 있는 동안 다른 액션이 먼저 방을 만들었을 수 있으므로 엔진 스레드에서 다시 확인한다.
        if (isOpenGroupExists(order.getOrderId())) {
            log.debug("이미 진행 중인 방이 있어 매칭 시작을 건너뜀: orderId={}", order.getOrderId());
            return;
        }

        List<WaitingDreami> top3List = dreamiMap.values().stream()
                .filter(dreami -> dreami.status() == WaitingDreamiStatus.MATCHING)
                .sorted(orderingComparator())
                .limit(MAX_OFFER_COUNT)
                .toList();

        // 상황 : 상위 3명 알림 보낼 드리미 완성
        LocalDateTime expiresAt = LocalDateTime.now().plus(OFFER_TTL);
        List<MatchOffer> matchOfferList = new ArrayList<>();
        for (WaitingDreami dreami : top3List) {
            UUID offerId = UUID.randomUUID(); // 제안UUID (드리미 1명당 1개)
            MatchOffer offer = new MatchOffer(
                    offerId, order.getOrderId(), dreami.dreamiId(), MatchOfferStatus.OFFERED, expiresAt);
            matchOfferList.add(offer);

            offersById.put(offerId, offer);
            offerIdsByDreamiId.computeIfAbsent(dreami.dreamiId(), k -> new HashSet<>()).add(offerId);
        }

        // 드리미 3명에게 status를 변경 요청
        for (WaitingDreami dreami : top3List) {
            dreamiMap.get(dreami.dreamiId()).markProposed();
        }
        OrderOfferGroup group = new OrderOfferGroup(order.getOrderId(), matchOfferList); // 방안에상위3명넣기 (사실상 방 만들기)
        if (matchOfferList.isEmpty()) {
            // 제안할 드리미가 한 명도 없으면 이 방은 바로 재매칭 대상이다.
            group.closeForRematch();
        }
        orderOfferGroupsByOrderId.put(order.getOrderId(), group);
        alarmBySocket("드리미에게_제안_팝업_띄우기");
    }

    // 팝업에서 수락을 눌렀다는 가정
    public void acceptByDreami(UUID offerId) {
        matchingEngine.submit(new AcceptByDreami(this, offerId));
    }

    void applyAcceptByDreami(UUID offerId) {
        log.debug("드리미 수락 액션 실행: offerId={}", offerId);

        Optional<MatchOffer> optionalMatchOffer = acceptableOffer(offerId);
        if (optionalMatchOffer.isEmpty()) {
            return;
        }
        MatchOffer matchOffer = optionalMatchOffer.get();
        OrderOfferGroup group = orderOfferGroupsByOrderId.get(matchOffer.orderId());
        if (group == null) {
            alarmBySocket("존재하지 않는 주문입니다.");
            return;
        }
        UUID acceptedDreamiId = matchOffer.dreamiId();

        // 수락한 사람의 상태를 PENDING_BOORMI_CONFIRMATION로 변경
        // 나머지 매칭오퍼 상태를 WITHDRAW로 변경
        for (MatchOffer offer : group.offers()) {
            // 수락한사람은 PENDING_BOORMI_CONFIRMATION
            // 나머지 사람은 WITHDRAWN
            if (offer.dreamiId().equals(acceptedDreamiId)) {
                offer.acceptByDreami();
                alarmBySocket("부르미한테_드리미정보_팝업넘기기");
            } else if (offer.status() == MatchOfferStatus.OFFERED) {
                // 아직 응답 대기중(OFFERED)인 오퍼만 회수한다.
                // 이미 거절/만료됐거나 다른 방으로 넘어간 드리미의 상태는 건드리지 않는다.
                offer.withdraw();
                // 선착순에서 패배한 드리미를 다시 매칭 수락가능한 상태로 변경
                findDreami(offer.dreamiId())
                        .ifPresent(WaitingDreami::markMatching);
                alarmBySocket("팝업꺼지게(선착순패배)");
            }
        }
    }

    // 드리미가 거절하면, DREAMI_REJECTED로 변경 및 다시 대기상태로
    public void rejectByDreami(UUID offerId) {
        matchingEngine.submit(new RejectByDreami(this, offerId));
    }

    void applyRejectByDreami(UUID offerId) {
        log.debug("드리미 거절 액션 실행: offerId={}", offerId);

        findOffer(offerId).ifPresentOrElse(
                offer -> {
                    findDreami(offer.dreamiId()).ifPresent(WaitingDreami::markMatching);
                    offer.rejectByDreami();
                    alarmBySocket("거절했으니 팝업끄면됨");
                    closeGroupIfExhausted(offer.orderId());
                },
                () -> alarmBySocket("존재하지 않는 제안입니다.")
        );
    }

    public void acceptByBoormi(UUID offerId) {
        matchingEngine.submit(new AcceptByBoormi(this, offerId));
    }

    void applyAcceptByBoormi(UUID offerId) {
        log.debug("부르미 수락 액션 실행: offerId={}", offerId);

        findOffer(offerId).ifPresentOrElse(
                matchOffer -> {
                    matchOffer.confirmByBoormi(); // 부르미까지 수락 완료
                    findOrderOfferGroup(matchOffer.orderId())
                            .ifPresent(OrderOfferGroup::markMatched);
                    proceedToDelivery(matchOffer);
                },
                () -> alarmBySocket("존재하지 않는 제안입니다.")
        );
    }

    public void rejectByBoormi(UUID offerId) {
        matchingEngine.submit(new RejectByBoormi(this, offerId));
    }

    void applyRejectByBoormi(UUID offerId) {
        log.debug("부르미 거절 액션 실행: offerId={}", offerId);

        findOffer(offerId).ifPresentOrElse(
                matchOffer -> {
                    matchOffer.rejectByBoormi();

                    // 거절당한 드리미의 상태를 배달가능 상태로 변경
                    alarmBySocket("거절당한_드리미에게_부르미가_거절했다고_알려주기");
                    findDreami(matchOffer.dreamiId())
                            .ifPresent(WaitingDreami::markMatching);

                    closeGroupForRematch(matchOffer.orderId());
                },
                () -> alarmBySocket("존재하지 않는 제안입니다.")
        );
    }

    public void expireDreamiOffer(UUID offerId) {
        matchingEngine.submit(new ExpireDreamiOffer(this, offerId));
    }

    void applyExpireDreamiOffer(UUID offerId) {
        log.debug("드리미 응답시간 만료 액션 실행: offerId={}", offerId);

        // 해당 match가 OFFERED 상태가 아니라면 다른 로직에 의해서 처리가 된거임
        findOffer(offerId)
                .filter(matchOffer -> matchOffer.status() == MatchOfferStatus.OFFERED)
                .ifPresent(matchOffer -> {
                    matchOffer.expireByDreami();
                    findDreami(matchOffer.dreamiId())
                            .ifPresent(WaitingDreami::markMatching);
                    closeGroupIfExhausted(matchOffer.orderId());
                });
    }

    public void expireBoormiOffer(UUID offerId) {
        matchingEngine.submit(new ExpireBoormiOffer(this, offerId));
    }

    void applyExpireBoormiOffer(UUID offerId) {
        log.debug("부르미 응답시간 만료 액션 실행: offerId={}", offerId);

        findOffer(offerId).ifPresent(matchOffer -> {
            // 드리미가 다시 배달이 가능하게 바꿔야함
            matchOffer.expireByBoormi();
            findDreami(matchOffer.dreamiId())
                    .ifPresent(WaitingDreami::markMatching);
            closeGroupForRematch(matchOffer.orderId());
        });
    }

    /**
     * TODO: 거리순 등 실제 정렬 기준 확정 전까지는 대기 오래한 순
     */
    private Comparator<WaitingDreami> orderingComparator() {
        return Comparator.comparing(WaitingDreami::updatedAt);
    }

    private boolean isOpenGroupExists(UUID orderId) {
        OrderOfferGroup existingGroup = orderOfferGroupsByOrderId.get(orderId);
        return existingGroup != null && existingGroup.status() == OrderOfferGroupStatus.OPEN;
    }

    private Optional<MatchOffer> findOffer(UUID offerId) {
        return Optional.ofNullable(offersById.get(offerId));
    }

    Optional<WaitingDreami> findDreami(UUID dreamiId) {
        return Optional.ofNullable(dreamiMap.get(dreamiId));
    }

    public Optional<OrderOfferGroup> findOrderOfferGroup(UUID orderId) {
        return Optional.ofNullable(orderOfferGroupsByOrderId.get(orderId));
    }

    /**
     * 확정 후보(수락자)를 부르미가 거절/만료시킨 경우 - 남은 오퍼가 없으므로 재매칭이 필요하다.
     */
    private void closeGroupForRematch(UUID orderId) {
        findOrderOfferGroup(orderId).ifPresent(OrderOfferGroup::closeForRematch);
    }

    /**
     * 방 안의 모든 오퍼가 거절/만료/철회로 끝나 더 이상 진행 중인 오퍼가 없으면 재매칭이 필요하다.
     */
    private void closeGroupIfExhausted(UUID orderId) {
        findOrderOfferGroup(orderId).ifPresent(group -> {
            boolean anyStillLive = group.offers().stream()
                    .anyMatch(offer -> offer.status() == MatchOfferStatus.OFFERED
                            || offer.status() == MatchOfferStatus.PENDING_BOORMI_CONFIRMATION
                            || offer.status() == MatchOfferStatus.MATCHED);
            if (!anyStillLive) {
                group.closeForRematch();
            }
        });
    }

    /**
     * 드리미가 정상적으로 수락 가능한 오퍼만 반환한다. 없거나 이미 종료된 상태면 실패 알림을 보내고 empty를 반환한다.
     */
    private Optional<MatchOffer> acceptableOffer(UUID offerId) {
        MatchOffer offer = offersById.get(offerId);
        if (offer == null) {
            alarmBySocket("존재하지 않는 제안입니다.");
            return Optional.empty();
        }
        // 이미 자신 matchOffer상태가 WITHDRAWN이면? -> 실패메시지
        if (offer.status() == MatchOfferStatus.WITHDRAWN) {
            alarmBySocket("이미 다른 드리미가 수락한 주문입니다.");
            return Optional.empty();
        }
        // 정상적으로 수락 가능한 상태는 OFFERED 뿐. (거절/만료된 제안은 수락 불가)
        if (offer.status() != MatchOfferStatus.OFFERED) {
            alarmBySocket("이미 종료된 제안입니다.");
            return Optional.empty();
        }
        return Optional.of(offer);
    }

    // ────────────────────────────── 미구현 ──────────────────────────────

    private void proceedToDelivery(MatchOffer matchOffer) {
        // 아직 코드 구현X
    }

    // ────────────────────────────── 조회 헬퍼 ──────────────────────────────

    public enum MatchOfferStatus {
        /**
         * 드리미에게 제안이 전달되어 응답 대기 중
         */
        OFFERED,
        /**
         * 해당 드리미가 수락하여 부르미의 승낙을 대기중
         */
        PENDING_BOORMI_CONFIRMATION,
        /**
         * 해당 드리미가 수락했고 부르미도 수락해 매칭 후보로 확정됨
         */
        MATCHED,
        /**
         * 해당 부르미가 명시적으로 거절함
         */
        BOORMI_REJECTED,
        /**
         * 해당 드리미가 명시적으로 거절함
         */
        DREAMI_REJECTED,
        /**
         * 제한 시간 내 부르미가 응답하지 않아 만료됨
         */
        BOORMI_EXPIRED,
        /**
         * 제한 시간 내 드리미가 응답하지 않아 만료됨
         */
        DREAMI_EXPIRED,
        /**
         * 다른 드리미가 먼저 수락했거나 서버가 제안을 회수함
         */
        WITHDRAWN
    }

    public enum WaitingDreamiStatus {
        /**
         * 지금 매칭 중
         */
        MATCHING,
        /**
         * 지금 Match Offer 방 안에 들어감
         */
        PROPOSED
    }

    public enum OrderOfferGroupStatus {
        /**
         * 제안 응답 대기 중 (드리미 수락 후 부르미 확인 대기 포함)
         */
        OPEN,
        /**
         * 드리미+부르미 모두 수락하여 매칭 확정
         */
        MATCHED,
        /**
         * 더 이상 유효한 제안이 없음. rematchRequired로 재매칭 필요 여부를 판단한다.
         */
        CLOSED
    }

    /**
     * status가 계속 바뀌므로 record가 아닌 가변 클래스로 변경. (record로 유지하려면 withStatus()로 새 인스턴스를 만들어 맵에 다시 넣어야 함)
     */
    public static final class MatchOffer {
        private final UUID offerId;
        private final UUID orderId;
        private final UUID dreamiId;
        private final LocalDateTime expiresAt;
        private MatchOfferStatus status;

        public MatchOffer(UUID offerId, UUID orderId, UUID dreamiId,
                          MatchOfferStatus status, LocalDateTime expiresAt) {
            this.offerId = offerId;
            this.orderId = orderId;
            this.dreamiId = dreamiId;
            this.status = status;
            this.expiresAt = expiresAt;
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

        public LocalDateTime expiresAt() {
            return expiresAt;
        }

        public MatchOfferStatus status() {
            return status;
        }

        public void acceptByDreami() {
            requireStatus(MatchOfferStatus.OFFERED);
            this.status = MatchOfferStatus.PENDING_BOORMI_CONFIRMATION;
        }

        public void rejectByDreami() {
            requireStatus(MatchOfferStatus.OFFERED);
            this.status = MatchOfferStatus.DREAMI_REJECTED;
        }

        public void withdraw() {
            requireStatus(MatchOfferStatus.OFFERED);
            this.status = MatchOfferStatus.WITHDRAWN;
        }

        public void confirmByBoormi() {
            requireStatus(MatchOfferStatus.PENDING_BOORMI_CONFIRMATION);
            this.status = MatchOfferStatus.MATCHED;
        }

        public void rejectByBoormi() {
            requireStatus(MatchOfferStatus.PENDING_BOORMI_CONFIRMATION);
            this.status = MatchOfferStatus.BOORMI_REJECTED;
        }

        public void expireByDreami() {
            requireStatus(MatchOfferStatus.OFFERED);
            this.status = MatchOfferStatus.DREAMI_EXPIRED;
        }

        public void expireByBoormi() {
            requireStatus(MatchOfferStatus.PENDING_BOORMI_CONFIRMATION);
            this.status = MatchOfferStatus.BOORMI_EXPIRED;
        }

        private void requireStatus(MatchOfferStatus expected) {
            if (this.status != expected) {
                throw new IllegalStateException(
                        "잘못된 상태 전이입니다: offerId=" + offerId + ", 현재상태=" + status + ", 기대상태=" + expected);
            }
        }
    }

    /**
     * 기다리고 있는 드리미 (콜 대기중인 드리미). 마찬가지로 status가 바뀌므로 가변 클래스
     */
    public static final class WaitingDreami {
        private final UUID dreamiId;
        private final GeoPoint location;
        private WaitingDreamiStatus status;
        private LocalDateTime updatedAt;

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

    /**
     * 한 주문에 대해 동시에 뿌린 제안 묶음("방"). 방 자체의 상태(OPEN/MATCHED/CLOSED)와 재매칭 필요 여부를 여기서 관리한다.
     */
    public static final class OrderOfferGroup {
        private final UUID orderId;
        private final List<MatchOffer> offers;
        private OrderOfferGroupStatus status;
        private boolean rematchRequired;

        public OrderOfferGroup(UUID orderId, List<MatchOffer> offers) {
            this.orderId = orderId;
            this.offers = offers;
            this.status = OrderOfferGroupStatus.OPEN;
            this.rematchRequired = false;
        }

        public UUID orderId() {
            return orderId;
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

        void markMatched() {
            requireStatus(OrderOfferGroupStatus.OPEN);
            this.status = OrderOfferGroupStatus.MATCHED;
        }

        void closeForRematch() {
            this.status = OrderOfferGroupStatus.CLOSED;
            this.rematchRequired = true;
        }

        private void requireStatus(OrderOfferGroupStatus expected) {
            if (this.status != expected) {
                throw new IllegalStateException(
                        "잘못된 상태 전이입니다: orderId=" + orderId + ", 현재상태=" + status + ", 기대상태=" + expected);
            }
        }
    }
}
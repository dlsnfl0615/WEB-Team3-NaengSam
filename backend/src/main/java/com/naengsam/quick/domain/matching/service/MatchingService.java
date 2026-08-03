package com.naengsam.quick.domain.matching.service;

import com.naengsam.quick.domain.matching.dto.GeoPoint;
import com.naengsam.quick.domain.matching.event.BoormiRejectedPayload;
import com.naengsam.quick.domain.matching.event.DreamiInfoPayload;
import com.naengsam.quick.domain.matching.event.MatchingEventType;
import com.naengsam.quick.domain.matching.event.NotificationErrorPayload;
import com.naengsam.quick.domain.matching.event.OfferClosedPayload;
import com.naengsam.quick.domain.matching.event.OfferPopupPayload;
import com.naengsam.quick.domain.order.entity.Orders;
import com.naengsam.quick.global.sse.SseService;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 부르미 - 드리미 매칭 로직 스켈레톤. 로직 자체는 원본 그대로 두고, 컴파일/자료구조/네이밍 일관성만 보정한 버전.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MatchingService {

    /**
     * 드리미 응답 제한시간. 제한은 30초 기준. //TODO: 현재 디버깅을 위해서 timeout을 3000초로 설정. 배포시 수정 필요.
     */
    private static final Duration OFFER_TTL = Duration.ofSeconds(3000);
    /**
     * 부르미 응답 제한시간. 제한은 30초 기준.
     */
    private static final Duration BOORMI_OFFER_TTL = Duration.ofSeconds(3000);
    /**
     * 한 주문에 동시에 제안할 최대 드리미 수
     */
    private static final int MAX_OFFER_COUNT = 3;
    /**
     * 재매칭 대기 방을 스캔하는 스케줄 주기. Fallback이기 때문에 10분으로.
     */
    private static final Duration REMATCH_SCAN_INTERVAL = Duration.ofMinutes(10);

    // ────────────────────────────── 도메인 타입 ──────────────────────────────
    // 모든 mutation은 엔진 스레드 하나에서만 일어나지만, 조회(findOrderOfferGroup 등)는 호출 스레드에서 직접 일어난다.
    // 맵 자체의 내부 구조 변경(put에 의한 리사이즈 등)이 다른 스레드의 읽기와 겹치면 HashMap은 안전하지 않으므로
    // 단일 기록자/다중 판독자 상황에서도 안전한 ConcurrentHashMap을 쓴다.
    private final Map<UUID, MatchOffer> offersById = new ConcurrentHashMap<>();           // Map<OfferUUID, MatchOffer>
    private final Map<UUID, Set<UUID>> offerIdsByDreamiId = new ConcurrentHashMap<>();    // Map<DreamiUUID, Set<OfferUUID>>

    // 하나의 주문에 대해 동시에 뿌린 제안 묶음 = "방"
    private final Map<UUID, OrderOfferGroup> orderOfferGroupsByOrderId = new ConcurrentHashMap<>();

    // ────────────────────────────── 저장소 ──────────────────────────────
    private final Map<UUID, WaitingDreami> dreamiMap = new ConcurrentHashMap<>();
    private final MatchingEngine matchingEngine;
    private final SseService sseService;
    private final OfferTimeoutScheduler offerTimeoutScheduler;

    public List<WaitingDreami> waitingDreamis() {
        return List.copyOf(dreamiMap.values());
    }

    // 외부에서는 이 메서드로 액션을 큐에 넣기만 한다. 실제 상태 변경은 엔진 스레드에서 apply*가 수행한다.
    public void registerDreami(UUID dreamiId, GeoPoint location) {
        matchingEngine.submit(new DreamiRegister(this, dreamiId, location));
    }

    void applyRegisterDreami(UUID dreamiId, GeoPoint location) {
        dreamiMap.put(dreamiId,
                new WaitingDreami(dreamiId, location, WaitingDreamiStatus.MATCHING, LocalDateTime.now()));
        log.debug("드리미 등록 처리 완료: dreamiId={}, location={}", dreamiId, location);
        // 재매칭 대기 중인 주문이 있으면 방금 등록된 드리미에게 오퍼를 시도한다.
//        retryRematchWaitingGroups();
    }

    /**
     * 드리미 등록 없이도 재매칭 대기 방이 방치되지 않도록, 주기적으로 재매칭을 시도한다. 엔진의 단일 기록자 스레드가 아닌 스케줄러 스레드에서 실행되므로, 상태를 직접 건드리지 않고 다른 액션들과 동일하게
     * 큐에 제출만 한다.
     */
    @Scheduled(fixedRate = 600_000L) // REMATCH_SCAN_INTERVAL과 동일한 값(ms) — @Scheduled는 상수 표현식만 허용
    public void scheduleRematchWaitingGroups() {
        matchingEngine.submit(new RematchWaitingGroups(this));
    }

    void applyRematchWaitingGroups() {
        retryRematchWaitingGroups();
    }

    /**
     * 재매칭 대기(CLOSED + rematchRequired) 상태의 방들에 대해 오퍼 라운드를 다시 시도한다. {@link #attemptOfferRound}는 그룹 맵의 키를 추가/삭제하지 않으므로
     * 스냅샷 순회로 안전하다.
     */
    private void retryRematchWaitingGroups() {
        List<OrderOfferGroup> waitingGroups = orderOfferGroupsByOrderId.values().stream()
                .filter(group -> group.status() == OrderOfferGroupStatus.CLOSED && group.rematchRequired())
                .toList();
        for (OrderOfferGroup group : waitingGroups) {
            attemptOfferRound(group);
        }
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

        OrderOfferGroup group = new OrderOfferGroup(order.getOrderId(), order.getBoormiId(), new ArrayList<>());
        orderOfferGroupsByOrderId.put(order.getOrderId(), group);
        attemptOfferRound(group);
    }

    /**
     * 방에 아직 제안받지 않은 대기 드리미가 있으면 다음 오퍼 라운드를 진행하고, 없으면 재매칭 대기(CLOSED)로 둔다. 최초 매칭 시작과 소진 후 재매칭이 모두 이 메서드를 재사용한다. 이 주문을 이미
     * 제안받은(거절/만료/철회) 드리미는 반복 알림을 피하기 위해 제외한다.
     */
    private void attemptOfferRound(OrderOfferGroup group) {
        Set<UUID> alreadyOffered = new HashSet<>();
        for (MatchOffer offer : group.offers()) {
            alreadyOffered.add(offer.dreamiId());
        }

        List<WaitingDreami> candidates = dreamiMap.values().stream()
                .filter(dreami -> dreami.status() == WaitingDreamiStatus.MATCHING)
                .filter(dreami -> !alreadyOffered.contains(dreami.dreamiId()))
                .sorted(orderingComparator())
                .limit(MAX_OFFER_COUNT)
                .toList();

        if (candidates.isEmpty()) {
            // 아직 붙일 드리미가 없으면 재매칭 대기 상태로 남긴다. (드리미 등록/소진 시 재시도)
            group.closeForRematch();
            return;
        }

        LocalDateTime expiresAt = LocalDateTime.now().plus(OFFER_TTL);
        List<MatchOffer> newOffers = new ArrayList<>();
        for (WaitingDreami dreami : candidates) {
            UUID offerId = UUID.randomUUID(); // 제안UUID (드리미 1명당 1개)
            MatchOffer offer = new MatchOffer(
                    offerId, group.orderId(), dreami.dreamiId(), MatchOfferStatus.OFFERED, expiresAt);
            newOffers.add(offer);

            offersById.put(offerId, offer);
            offerIdsByDreamiId.computeIfAbsent(dreami.dreamiId(), k -> new HashSet<>()).add(offerId);
            dreami.markProposed();
            offerTimeoutScheduler.scheduleDreamiOfferTimeout(offerId, expiresAt);
        }
        group.addOffersAndOpen(newOffers);

        // 제안받은 드리미 각각에게 제안 팝업을 띄운다.
        for (MatchOffer offer : newOffers) {
            sseService.send(offer.dreamiId(), MatchingEventType.OFFER_POPUP, OfferPopupPayload.from(offer));
        }
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
            sseService.send(matchOffer.dreamiId(), MatchingEventType.OFFER_ERROR,
                    new NotificationErrorPayload("존재하지 않는 주문입니다."));
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
                offerTimeoutScheduler.scheduleBoormiOfferTimeout(offer.offerId(),
                        LocalDateTime.now().plus(BOORMI_OFFER_TTL));
                // 부르미에게 수락한 드리미 정보를 넘겨 확인 팝업을 띄운다.
                sseService.send(group.boormiId(), MatchingEventType.DREAMI_INFO, DreamiInfoPayload.from(offer));
            } else if (offer.status() == MatchOfferStatus.OFFERED) {
                // 아직 응답 대기중(OFFERED)인 오퍼만 회수한다.
                // 이미 거절/만료됐거나 다른 방으로 넘어간 드리미의 상태는 건드리지 않는다.
                offer.withdraw();
                // 선착순에서 패배한 드리미를 다시 매칭 수락가능한 상태로 변경
                findDreami(offer.dreamiId())
                        .ifPresent(WaitingDreami::markMatching);
                sseService.send(offer.dreamiId(), MatchingEventType.OFFER_CLOSED,
                        new OfferClosedPayload(offer.offerId(), "선착순 마감"));
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
                    sseService.send(offer.dreamiId(), MatchingEventType.OFFER_CLOSED,
                            new OfferClosedPayload(offer.offerId(), "거절 완료"));
                    closeGroupIfExhausted(offer.orderId());
                },
                () -> log.debug("존재하지 않는 제안 거절 요청, 무시: offerId={}", offerId)
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
                () -> log.debug("존재하지 않는 제안 부르미 수락 요청, 무시: offerId={}", offerId)
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

                    // 거절당한 드리미에게 부르미가 거절했음을 알리고, 다시 배달가능 상태로 변경
                    sseService.send(matchOffer.dreamiId(), MatchingEventType.BOORMI_REJECTED,
                            new BoormiRejectedPayload(matchOffer.offerId(), matchOffer.orderId()));
                    findDreami(matchOffer.dreamiId())
                            .ifPresent(WaitingDreami::markMatching);

                    closeGroupForRematch(matchOffer.orderId());
                },
                () -> log.debug("존재하지 않는 제안 부르미 거절 요청, 무시: offerId={}", offerId)
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
     * 부르미가 매칭 진행 중인 주문을 직접 취소한다. 아직 방이 없거나 이미 종료된 방이면 아무 일도 일어나지 않는다.
     */
    public void cancelOrderByBoormi(UUID orderId) {
        matchingEngine.submit(new CancelOrderByBoormi(this, orderId));
    }

    void applyCancelOrderByBoormi(UUID orderId) {
        log.debug("부르미 주문 취소 액션 실행: orderId={}", orderId);

        OrderOfferGroup group = orderOfferGroupsByOrderId.get(orderId);
        if (group == null) {
            log.debug("존재하지 않는 주문 취소 요청, 무시: orderId={}", orderId);
            return;
        }
        if (group.status() != OrderOfferGroupStatus.OPEN) {
            log.debug("이미 종료된 주문 취소 요청, 무시: orderId={}", orderId);
            return;
        }

        for (MatchOffer offer : group.offers()) {
            if (offer.status() == MatchOfferStatus.OFFERED) {
                offer.withdraw();
                findDreami(offer.dreamiId()).ifPresent(WaitingDreami::markMatching);
                sseService.send(offer.dreamiId(), MatchingEventType.OFFER_CLOSED,
                        new OfferClosedPayload(offer.offerId(), "부르미가 주문을 취소함"));
            } else if (offer.status() == MatchOfferStatus.PENDING_BOORMI_CONFIRMATION) {
                offer.rejectByBoormi();
                findDreami(offer.dreamiId()).ifPresent(WaitingDreami::markMatching);
                sseService.send(offer.dreamiId(), MatchingEventType.OFFER_CLOSED,
                        new OfferClosedPayload(offer.offerId(), "부르미가 주문을 취소함"));
            }
        }
        group.cancel();
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
     * 확정 후보(수락자)를 부르미가 거절/만료시킨 경우 - 남은 오퍼가 없으므로 아직 제안받지 않은 드리미에게 즉시 재오퍼를 시도한다. 후보가 없으면 재매칭 대기 상태가 된다.
     */
    private void closeGroupForRematch(UUID orderId) {
        findOrderOfferGroup(orderId).ifPresent(this::attemptOfferRound);
    }

    /**
     * 방 안의 모든 오퍼가 거절/만료/철회로 끝나 더 이상 진행 중인 오퍼가 없으면, 아직 제안받지 않은 드리미에게 즉시 재오퍼를 시도한다. 후보가 없으면 재매칭 대기 상태가 된다.
     */
    private void closeGroupIfExhausted(UUID orderId) {
        findOrderOfferGroup(orderId).ifPresent(group -> {
            boolean anyStillLive = group.offers().stream()
                    .anyMatch(offer -> offer.status() == MatchOfferStatus.OFFERED
                            || offer.status() == MatchOfferStatus.PENDING_BOORMI_CONFIRMATION
                            || offer.status() == MatchOfferStatus.MATCHED);
            if (!anyStillLive) {
                attemptOfferRound(group);
            }
        });
    }

    /**
     * 드리미가 정상적으로 수락 가능한 오퍼만 반환한다. 없거나 이미 종료된 상태면 실패 알림을 보내고 empty를 반환한다.
     */
    private Optional<MatchOffer> acceptableOffer(UUID offerId) {
        MatchOffer offer = offersById.get(offerId);
        if (offer == null) {
            // 대상 드리미를 특정할 수 없으므로 SSE로 알릴 수 없다. 로그만 남긴다.
            log.debug("존재하지 않는 제안 수락 요청, 무시: offerId={}", offerId);
            return Optional.empty();
        }
        // 이미 자신 matchOffer상태가 WITHDRAWN이면? -> 실패메시지
        if (offer.status() == MatchOfferStatus.WITHDRAWN) {
            sseService.send(offer.dreamiId(), MatchingEventType.OFFER_ERROR,
                    new NotificationErrorPayload("이미 다른 드리미가 수락한 주문입니다."));
            return Optional.empty();
        }
        // 정상적으로 수락 가능한 상태는 OFFERED 뿐. (거절/만료된 제안은 수락 불가)
        if (offer.status() != MatchOfferStatus.OFFERED) {
            sseService.send(offer.dreamiId(), MatchingEventType.OFFER_ERROR,
                    new NotificationErrorPayload("이미 종료된 제안입니다."));
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
        // 엔진 스레드(단일 기록자)가 쓰고 호출 스레드(다중 판독자)가 동기화 없이 읽으므로 volatile로 가시성을 보장한다.
        private volatile MatchOfferStatus status;

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

    /**
     * 한 주문에 대해 동시에 뿌린 제안 묶음("방"). 방 자체의 상태(OPEN/MATCHED/CLOSED)와 재매칭 필요 여부를 여기서 관리한다.
     */
    public static final class OrderOfferGroup {
        private final UUID orderId;
        private final UUID boormiId;
        private final List<MatchOffer> offers;
        // 엔진 스레드(단일 기록자)가 쓰고 호출 스레드(다중 판독자)가 동기화 없이 읽으므로 volatile로 가시성을 보장한다.
        private volatile OrderOfferGroupStatus status;
        private volatile boolean rematchRequired;

        public OrderOfferGroup(UUID orderId, UUID boormiId, List<MatchOffer> offers) {
            this.orderId = orderId;
            this.boormiId = boormiId;
            // 라운드마다 엔진 스레드가 append하는 동시에 다른 스레드가 offers()로 읽으므로,
            // ArrayList가 아닌 CopyOnWriteArrayList로 보관해 순회/복사 중 경합을 피한다.
            this.offers = new CopyOnWriteArrayList<>(offers);
            this.status = OrderOfferGroupStatus.OPEN;
            this.rematchRequired = false;
        }

        public UUID orderId() {
            return orderId;
        }

        public UUID boormiId() {
            return boormiId;
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

        /**
         * 새 오퍼 라운드를 추가하며 방을 다시 진행중(OPEN) 상태로 되돌린다. 재매칭/최초 오퍼 모두 이 경로를 사용한다.
         */
        void addOffersAndOpen(List<MatchOffer> newOffers) {
            this.offers.addAll(newOffers);
            this.status = OrderOfferGroupStatus.OPEN;
            this.rematchRequired = false;
        }

        void closeForRematch() {
            this.status = OrderOfferGroupStatus.CLOSED;
            this.rematchRequired = true;
        }

        /**
         * 부르미가 직접 주문을 취소한 경우. 재매칭 대상이 아니므로 rematchRequired는 세우지 않는다.
         */
        void cancel() {
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
}
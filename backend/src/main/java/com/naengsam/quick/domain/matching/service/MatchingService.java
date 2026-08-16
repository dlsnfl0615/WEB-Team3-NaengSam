package com.naengsam.quick.domain.matching.service;

import com.naengsam.quick.domain.delivery.service.DeliveryService;
import com.naengsam.quick.domain.matching.dto.GeoPoint;
import com.naengsam.quick.domain.matching.event.BoormiConfirmedEvent;
import com.naengsam.quick.domain.matching.event.BoormiRejectedDreamiEvent;
import com.naengsam.quick.domain.matching.event.BoormiRejectedPayload;
import com.naengsam.quick.domain.matching.event.DreamiAcceptedEvent;
import com.naengsam.quick.domain.matching.event.DreamiInfoPayload;
import com.naengsam.quick.domain.matching.event.MatchingEventType;
import com.naengsam.quick.domain.matching.event.MatchingStartRequestedEvent;
import com.naengsam.quick.domain.matching.event.NotificationErrorPayload;
import com.naengsam.quick.domain.matching.event.OfferClosedPayload;
import com.naengsam.quick.domain.matching.event.OrderCancelledByBoormiEvent;
import com.naengsam.quick.domain.matching.model.MatchOffer;
import com.naengsam.quick.domain.matching.model.MatchOfferStatus;
import com.naengsam.quick.domain.matching.model.OrderOfferGroup;
import com.naengsam.quick.domain.matching.model.OrderOfferGroupStatus;
import com.naengsam.quick.domain.matching.model.WaitingDreami;
import com.naengsam.quick.domain.matching.model.WaitingDreamiStatus;
import com.naengsam.quick.domain.matching.model.WaitingOrder;
import com.naengsam.quick.domain.matching.policy.assignment.MatchingAssignmentPolicy;
import com.naengsam.quick.domain.matching.policy.assignment.MatchingAssignmentProblem;
import com.naengsam.quick.domain.matching.policy.assignment.MatchingAssignmentProblemAssembler;
import com.naengsam.quick.domain.matching.policy.assignment.MatchingPlan;
import com.naengsam.quick.domain.matching.policy.assignment.MatchingPlanApplier;
import com.naengsam.quick.domain.matching.policy.config.MatchingPolicyProperties;
import com.naengsam.quick.domain.matching.service.engine.MatchingEngine;
import com.naengsam.quick.domain.order.dto.OrderSummaryDto;
import com.naengsam.quick.domain.order.entity.OrderCd;
import com.naengsam.quick.domain.order.entity.Orders;
import com.naengsam.quick.domain.order.service.BoormiOfferExpirationService;
import com.naengsam.quick.domain.order.service.OrderService;
import com.naengsam.quick.domain.order.service.PendingOfferStateService;
import com.naengsam.quick.global.notification.NotificationService;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 부르미 - 드리미 매칭 로직 스켈레톤. 로직 자체는 원본 그대로 두고, 컴파일/자료구조/네이밍 일관성만 보정한 버전.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MatchingService {

    /**
     * 드리미 응답 제한시간. 제한은 30초 기준.
     */
    private static final Duration OFFER_TTL = Duration.ofSeconds(30);
    /**
     * 부르미 응답 제한시간. 제한은 30초 기준.
     */
    private static final Duration BOORMI_OFFER_TTL = Duration.ofSeconds(30);
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
    private final NotificationService notificationService;
    private final DeliveryService deliveryService;
    private final Clock clock;
    private final MatchingAssignmentProblemAssembler matchingAssignmentProblemAssembler;
    private final MatchingAssignmentPolicy matchingAssignmentPolicy;
    private final MatchingPlanApplier matchingPlanApplier;
    private final MatchingPolicyProperties matchingPolicyProperties;
    private final GeoDistanceCalculator geoDistanceCalculator;
    private final MeterRegistry meterRegistry;
    // 부르미 응답 timeout을 DB 주문에도 반영하는 트랜잭션 서비스. MatchingService가 OrderRepository를
    // 직접 다루지 않도록 분리했다(도메인 간 독립성 유지) — 자세한 이유는 이 서비스의 Javadoc 참고.
    private final BoormiOfferExpirationService boormiOfferExpirationService;
    private final OrderService orderService;
    // 부르미 확정 액션이 큐에 쌓여 있는 동안 DB 주문이 다른 경로로 바뀌었는지 확인하는 검증 서비스. 위와 같은 이유로
    // MatchingService가 OrderRepository를 직접 다루지 않도록 분리했다.
    private final PendingOfferStateService pendingOfferStateService;

    public List<WaitingDreami> waitingDreamis() {
        return List.copyOf(dreamiMap.values());
    }

    /**
     * 지금 실시간 채널로 닿을 수 있는(= SSE 연결이 살아 있는) 대기 드리미만 남긴다.
     *
     * <p>{@code goOffline}을 호출하지 못하고 브라우저가 죽은 드리미(앱 스와이프 종료, 탭 메모리 회수 등)는 여전히
     * {@code dreamiMap}에 MATCHING으로 남아 있다. 이 유령에게 오퍼 슬롯이 가면 주문은 30초 TTL을 그대로 태우고 재매칭 대기로 되돌아가며, 그 뒤 쿨다운 정책까지 적용된다. 웹푸시로
     * 깨우더라도 30초 안에 알림 확인 → 잠금해제 → 앱 로딩 → 수락까지 끝내는 것은 사실상 불가능하므로, 판정 기준은 "푸시 구독 보유"가 아니라 "살아 있는 SSE 연결"이다.
     *
     * <p>엔진 스레드에서 호출되지만 판정은 {@code ConcurrentHashMap} 조회 한 번이라 블로킹이 없다.
     */
    private List<WaitingDreami> reachableWaitingDreamis() {
        return dreamiMap.values().stream()
                .filter(dreami -> dreami.status() == WaitingDreamiStatus.MATCHING)
                .filter(this::isReachable)
                .toList();
    }

    /**
     * 드리미에게 실시간으로 닿을 수 있는지 판정하고, 걸러낸 경우 그 사실을 지표로 남긴다. 필터가 실제로 몇 명의 유령을 막았는지는
     * {@code matching.candidates.filtered{reason=not_connected}}로 Grafana에서 확인한다.
     */
    private boolean isReachable(WaitingDreami dreami) {
        if (notificationService.isReachableNow(dreami.dreamiId())) {
            return true;
        }
        meterRegistry.counter("matching.candidates.filtered", "reason", "not_connected").increment();
        return false;
    }

    /**
     * 매칭 시작 후(WAITING/OPEN) 아직 확정되지 않은, 대기 중인 주문 목록을 조회한다. 한 부르미가 여러 주문을 동시에 가질 수 있으므로 부르미 단위가 아니라 주문 단위로 도출한다. 별도 등록 큐
     * 없이 {@link #startMatching}/{@link #cancelOrderByBoormi}로만 대기 상태가 결정되므로, 진행 중인 {@link OrderOfferGroup}에서 직접 도출한다.
     */
    public List<WaitingOrder> waitingOrders() {
        return orderOfferGroupsByOrderId.values().stream()
                .filter(OrderOfferGroup::isActive)
                .map(group -> new WaitingOrder(group.orderId(), group.location()))
                .toList();
    }

    /**
     * 상태와 무관한 전체 주문 그룹 목록. 상태별 필터링은 호출자(예:
     * {@link com.naengsam.quick.domain.matching.policy.assignment.MatchingAssignmentProblemAssembler})가 담당한다.
     */
    public List<OrderOfferGroup> orderOfferGroups() {
        return List.copyOf(orderOfferGroupsByOrderId.values());
    }

    // ────────────────────────────── 외부 API ──────────────────────────────
    // 외부에서는 이 메서드로 액션을 큐에 넣기만 한다. 실제 상태 변경은 엔진 스레드에서 apply*가 수행한다.

    /**
     * 드리미를 대기열에 등록한다. 호출 스레드에서 곧바로 확인 가능한 중복 등록만 빠르게 걸러내며, 이미 등록되어 있는 드리미면 큐에 넣지 않고 false를 반환하면서 실패 사유를 SSE로 알린다. 실제
     * 등록은 엔진 스레드에서 순차 처리된다.
     *
     * @param dreamiId 등록할 드리미 UUID
     * @param location 드리미의 현재 위치
     * @return 드리미 등록 액션이 큐에 제출되었으면 true, 이미 등록되어 있거나 큐 제출에 실패했을 경우 false
     */
    /**
     * 드리미가 매칭 엔진에 온라인 등록되어 오퍼를 기다리는 중인지. 이 상태는 DB에 흔적이 없어 주문 테이블만으로는 알 수 없다. 인메모리 상태이므로 서버가 재시작하면 false가 되며, 그때는 실제로도
     * 오프라인이 맞다.
     */
    public boolean isDreamiWaiting(UUID dreamiId) {
        return dreamiMap.containsKey(dreamiId);
    }

    public boolean registerDreami(UUID dreamiId, GeoPoint location) {
        if (dreamiMap.containsKey(dreamiId)) {
            notificationService.notify(dreamiId, MatchingEventType.OFFER_ERROR,
                    new NotificationErrorPayload(null, "이미 등록된 드리미입니다."));
            return false;
        }
        return matchingEngine.submit(new DreamiRegister(this, dreamiId, location));
    }

    /**
     * 드리미 등록을 해제한다. 호출 스레드에서 곧바로 확인 가능한 존재 여부만 빠르게 걸러내며, 등록되어 있지 않은 드리미면 큐에 넣지 않고 false를 반환하면서 실패 사유를 SSE로 알린다. 실제 제거는
     * 엔진 스레드에서 순차 처리된다.
     *
     * @param dreamiId 제거할 드리미 UUID
     * @return 드리미 제거 액션이 큐에 제출되었으면 true, 등록되어 있지 않거나 큐 제출에 실패했을 경우 false
     */
    public boolean removeDreami(UUID dreamiId) {
        if (!dreamiMap.containsKey(dreamiId)) {
            notificationService.notify(dreamiId, MatchingEventType.OFFER_ERROR,
                    new NotificationErrorPayload(null, "등록되지 않은 드리미입니다."));
            return false;
        }
        return matchingEngine.submit(new DreamiRemove(this, dreamiId));
    }

    /**
     * 매칭 시작을 요청한다. 호출 스레드에서 곧바로 확인 가능한 중복 시작만 빠르게 걸러내며, 이미 진행 중인 방이 있으면 큐에 넣지 않고 false를 반환한다. 실제 방 생성은 엔진 스레드에서 순차
     * 처리되며, 그 결과는 {@link #findOrderOfferGroup(UUID)}로 별도 조회한다.
     *
     * @param order 매칭을 시작할 주문
     * @return 매칭 시작 액션이 큐에 제출되었으면 true, 이미 진행 중인 방이 있거나 큐 제출에 실패했을 경우 false
     */
    public boolean startMatching(Orders order) {
        if (isActiveGroupExists(order.getOrderId())) {
            return false;
        }
        return matchingEngine.submit(new StartMatching(this, order));
    }

    /**
     * 주문 접수 트랜잭션이 커밋된 뒤에만 매칭엔진에 매칭 시작을 제출한다. 커밋 전에 제출하면 엔진이 오퍼 팝업을 먼저 보내, 드리미가 아직 저장되지 않은 주문을 수락하려 할 수 있다. 롤백된 접수는 이벤트가
     * 폐기되어 엔진까지 가지 않는다.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMatchingStartRequested(MatchingStartRequestedEvent event) {
        startMatching(event.order());
    }

    /**
     * 부르미가 매칭 진행 중인 주문을 직접 취소한다. 호출 스레드에서는 방의 존재·활성 상태를 미리 판단하지 않고 항상 취소
     * 액션을 큐에 제출한다 — 큐에 쌓여 있는 동안 다른 액션이 먼저 방을 종료시켰을 수 있어, 사전 검사 시점과 실제 실행
     * 시점의 상태가 다를 수 있기 때문이다. 실제 취소 가능 여부(방 존재·활성 상태)는 엔진 스레드의
     * {@link #applyCancelOrderByBoormi}에서 판단하며, 방이 없거나 이미 종료된 방이면 멱등하게 아무 일도 하지 않는다.
     *
     * @param orderId 취소할 주문 UUID
     * @return 취소 액션이 큐에 제출되었으면 true, 큐 제출 자체에 실패했을 경우 false
     */
    public boolean cancelOrderByBoormi(UUID orderId) {
        return matchingEngine.submit(new CancelOrderByBoormi(this, orderId));
    }

    /**
     * 주문 취소 트랜잭션이 커밋된 뒤에만 매칭엔진에 제안 회수를 제출한다. 커밋 전에 제출하면 취소가 롤백돼도 인메모리 방은 이미 종료된 채로 남아, 주문이 영영 재매칭되지 않는다.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderCancelledByBoormi(OrderCancelledByBoormiEvent event) {
        cancelOrderByBoormi(event.orderId());
    }

    /**
     * 드리미가 제안(팝업)을 수락한다. 큐 제출 전에는 유효성을 검사하지 않으며, 이미 종료/회수된 제안이거나 존재하지 않는 제안이면 엔진 스레드에서 실패를 판단해 SSE로 알린다. 수락이 확정되면 나머지
     * 오퍼는 회수(WITHDRAWN)되고 부르미에게 확인 팝업이 전달된다.
     *
     * @param offerId 수락할 제안 UUID
     */
    public void acceptByDreami(UUID offerId) {
        matchingEngine.submit(new AcceptByDreami(this, offerId));
    }

    /**
     * 드리미 수락 트랜잭션이 커밋된 뒤에만 매칭엔진에 수락을 제출한다. 엔진은 곧바로 부르미에게 확인 팝업을 보내는데, 부르미의 확정은 주문이 PENDING_BOORMI_CONFIRMATION 인지 검사하므로
     * 커밋 전에 제출하면 그 확정이 실패한다.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDreamiAccepted(DreamiAcceptedEvent event) {
        acceptByDreami(event.offerId());
    }

    /**
     * 드리미가 제안(팝업)을 거절한다. 거절한 드리미는 다시 매칭 대기(MATCHING) 상태로 돌아가고, 방에 더 이상 진행 중인 오퍼가 없으면 WAITING으로 전환되며 배치 디스패처에 dirty가
     * 표시되어 다음 batch에서 재평가된다.
     *
     * @param offerId 거절할 제안 UUID
     */
    public void rejectByDreami(UUID offerId) {
        matchingEngine.submit(new RejectByDreami(this, offerId));
    }

    /**
     * 부르미가 드리미의 수락을 최종 승인한다. 승인되면 해당 오퍼는 확정(MATCHED)되고 방도 매칭 완료 상태가 된다.
     *
     * @param offerId 승인할 제안 UUID
     */
    public void acceptByBoormi(UUID offerId) {
        matchingEngine.submit(new AcceptByBoormi(this, offerId));
    }

    /**
     * 부르미 확정 트랜잭션이 커밋된 뒤에만 매칭엔진에 수락을 제출한다. 엔진 스레드는 별도 트랜잭션으로 주문을 다시 읽어 배달을 시작하므로, 커밋 전에 제출하면 아직 IN_PROGRESS 가 아닌 주문을 보고
     * 배달 시작이 실패한다. 롤백된 확정은 이벤트가 폐기되어 엔진까지 가지 않는다.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBoormiConfirmed(BoormiConfirmedEvent event) {
        acceptByBoormi(event.offerId());
    }

    /**
     * 부르미가 드리미의 수락을 거절한다. 거절당한 드리미는 다시 매칭 대기(MATCHING) 상태로 돌아가고, 방은 WAITING으로 전환되며 배치 디스패처에 dirty가 표시되어 다음 batch에서
     * 재평가된다.
     *
     * @param offerId 거절할 제안 UUID
     */
    public void rejectByBoormi(UUID offerId) {
        matchingEngine.submit(new RejectByBoormi(this, offerId));
    }

    /**
     * 부르미 거절 트랜잭션이 커밋된 뒤에만 매칭엔진에 거절을 제출한다. 엔진은 곧바로 재오퍼를 돌리므로, 커밋 전에 제출하면 다른 드리미가 먼저 수락해 커밋한 PENDING_BOORMI_CONFIRMATION
     * 을 이 트랜잭션의 MATCHING 복귀가 덮어써 주문이 고착된다.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBoormiRejectedDreami(BoormiRejectedDreamiEvent event) {
        rejectByBoormi(event.offerId());
    }

    // ────────────────────────────── 내부 구현체 ──────────────────────────────
    void applyRegisterDreami(UUID dreamiId, GeoPoint location) {
        dreamiMap.put(dreamiId,
                new WaitingDreami(dreamiId, location, WaitingDreamiStatus.MATCHING, LocalDateTime.now(clock)));
        log.debug("드리미 등록 처리 완료: dreamiId={}, location={}", dreamiId, location);
    }

    void applyRemoveDreami(UUID dreamiId) {
        dreamiMap.remove(dreamiId);
        log.debug("드리미 제거 처리 완료: dreamiId={}", dreamiId);
    }

    /**
     * 정책 기반 배치 매칭 한 회차(스냅샷 조립 → 배정안 산출 → 검증 → 적용)를 큐에 제출한다. 네 단계 전부가 엔진 스레드에서 하나의 액션으로 실행되므로, 조립 시점의 스냅샷과 적용 시점의 실제
     * dreamiMap/방 상태 사이에 다른 액션이 끼어들 수 없다.
     *
     * @return 배치 매칭 액션이 큐에 제출되었으면 true
     */
    public boolean runMatchingAssignmentCycle() {
        return matchingEngine.submit(new RunMatchingAssignmentCycle(this));
    }

    void applyRunMatchingAssignmentCycle() {
        MatchingAssignmentProblem problem = matchingAssignmentProblemAssembler.assemble(
                orderOfferGroups(), reachableWaitingDreamis());
        MatchingPlan plan = matchingAssignmentPolicy.createPlan(problem);
        matchingPlanApplier.apply(problem, plan, LocalDateTime.now(clock),
                orderOfferGroupsByOrderId, dreamiMap, offersById, offerIdsByDreamiId);
    }

    void applyStartMatching(Orders order) {
        UUID orderId = order.getOrderId();
        log.debug("매칭 시작 액션 실행: orderId={}", orderId);

        // 큐에 쌓여 있는 동안 다른 액션이 먼저 방을 만들었을 수 있으므로 엔진 스레드에서 다시 확인한다.
        if (isActiveGroupExists(orderId)) {
            log.debug("이미 진행 중인 방이 있어 매칭 시작을 건너뜀: orderId={}", orderId);
            return;
        }

        // 큐에 쌓여 있는 동안 취소 등으로 주문 상태가 바뀌었을 수 있으므로, 액션이 들고 온 order(제출 시점 스냅샷)를
        // 그대로 믿지 않고 orderId로 최신 DB 엔티티를 다시 조회한다. 취소 이벤트가 유실돼도 여기서 최종적으로 막힌다.
        Optional<Orders> latestOrder = orderService.findOrder(orderId);
        if (latestOrder.isEmpty() || latestOrder.get().getOrderCd() != OrderCd.MATCHING) {
            log.debug("주문이 없거나 매칭 시작 가능한 상태(MATCHING)가 아니라 건너뜀: orderId={}", orderId);
            return;
        }

        Orders latest = latestOrder.get();
        GeoPoint boormiLocation = new GeoPoint(latest.getOriginLatitude(), latest.getOriginLongitude());
        OrderOfferGroup group = new OrderOfferGroup(latest.getOrderId(), latest.getBoormiId(), boormiLocation,
                OrderSummaryDto.from(latest), new ArrayList<>(), LocalDateTime.now(clock));
        orderOfferGroupsByOrderId.put(latest.getOrderId(), group);
    }

    void applyCancelOrderByBoormi(UUID orderId) {
        log.debug("부르미 주문 취소 액션 실행: orderId={}", orderId);

        OrderOfferGroup group = orderOfferGroupsByOrderId.get(orderId);
        if (group == null) {
            log.debug("존재하지 않는 주문 취소 요청, 무시: orderId={}", orderId);
            return;
        }
        if (!group.isActive()) {
            log.debug("이미 종료된 주문 취소 요청, 무시: orderId={}", orderId);
            return;
        }

        LocalDateTime now = LocalDateTime.now(clock);
        for (MatchOffer offer : group.offers()) {
            if (offer.status() == MatchOfferStatus.OFFERED) {
                offer.withdraw(now);
                releaseDreami(offer.dreamiId());
                notificationService.notify(offer.dreamiId(), MatchingEventType.OFFER_CLOSED,
                        new OfferClosedPayload(offer.offerId(), "부르미가 주문을 취소함"));
            } else if (offer.status() == MatchOfferStatus.PENDING_BOORMI_CONFIRMATION) {
                offer.rejectByBoormi(now);
                releaseDreami(offer.dreamiId());
                notificationService.notify(offer.dreamiId(), MatchingEventType.OFFER_CLOSED,
                        new OfferClosedPayload(offer.offerId(), "부르미가 주문을 취소함"));
            }
        }
        group.cancel();
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
            notificationService.notify(matchOffer.dreamiId(), MatchingEventType.OFFER_ERROR,
                    new NotificationErrorPayload(matchOffer.offerId(), "존재하지 않는 주문입니다."));
            return;
        }
        UUID acceptedOfferId = matchOffer.offerId();
        LocalDateTime now = LocalDateTime.now(clock);

        // 수락한 사람의 상태를 PENDING_BOORMI_CONFIRMATION로 변경
        // 나머지 매칭오퍼 상태를 WITHDRAW로 변경
        for (MatchOffer offer : group.offers()) {
            // 수락한 오퍼(offerId로 식별)는 PENDING_BOORMI_CONFIRMATION, 나머지는 WITHDRAWN.
            // dreamiId로 매칭하면 쿨다운 재평가로 같은 드리미에게 재발급된 새 오퍼와 이력에 남은 이전 종료
            // 오퍼(DREAMI_EXPIRED 등)를 동시에 건드리게 되어 이미 종료된 오퍼에서 잘못된 상태 전이가 난다.
            if (offer.offerId().equals(acceptedOfferId)) {
                offer.acceptByDreami(now);
                matchingEngine.schedule(new ExpireBoormiOffer(this, offer.offerId()), BOORMI_OFFER_TTL);
                // 부르미에게 수락한 드리미 정보를 넘겨 확인 팝업을 띄운다.
                notificationService.notify(group.boormiId(), MatchingEventType.DREAMI_INFO,
                        DreamiInfoPayload.from(offer, pickupEtaMinutesForOffer(offer), BOORMI_OFFER_TTL));
            } else if (offer.status() == MatchOfferStatus.OFFERED) {
                // 아직 응답 대기중(OFFERED)인 오퍼만 회수한다.
                // 이미 거절/만료됐거나 다른 방으로 넘어간 드리미의 상태는 건드리지 않는다.
                offer.withdraw(now);
                // 선착순에서 패배한 드리미를 다시 매칭 수락가능한 상태로 돌려, 다른 대기 주문의 배치 후보가 되게 한다.
                releaseDreami(offer.dreamiId());
                notificationService.notify(offer.dreamiId(), MatchingEventType.OFFER_CLOSED,
                        new OfferClosedPayload(offer.offerId(), "선착순 마감"));
            }
        }
    }

    void applyRejectByDreami(UUID offerId) {
        log.debug("드리미 거절 액션 실행: offerId={}", offerId);

        findOffer(offerId)
                .filter(offer -> offer.status() == MatchOfferStatus.OFFERED)
                .ifPresentOrElse(
                        offer -> {
                            releaseDreami(offer.dreamiId());
                            offer.rejectByDreami(LocalDateTime.now(clock));
                            notificationService.notify(offer.dreamiId(), MatchingEventType.OFFER_CLOSED,
                                    new OfferClosedPayload(offer.offerId(), "거절 완료"));
                            moveGroupToWaitingIfExhausted(offer.orderId());
                        },
                        () -> log.debug("거절 가능한 상태가 아닌 제안 거절 요청, 무시: offerId={}", offerId)
                );
    }

    void applyAcceptByBoormi(UUID offerId) {
        log.debug("부르미 수락 액션 실행: offerId={}", offerId);

        findOffer(offerId).ifPresentOrElse(
                matchOffer -> {
                    // 큐에 쌓여 있는 동안 DB 주문이 이 오퍼/드리미와 더 이상 일치하지 않게 바뀌었을 수 있으므로
                    // (예: 타임아웃이 먼저 처리돼 이미 MATCHING으로 되돌아간 뒤 뒤늦게 실행되는 경우), 최신 DB
                    // 상태를 마지막으로 확인한다.
                    if (!pendingOfferStateService.isCurrent(matchOffer.orderId(), offerId, matchOffer.dreamiId())) {
                        log.debug("DB 주문 상태가 이 오퍼와 더 이상 일치하지 않아 부르미 수락을 무시함: offerId={}, orderId={}",
                                offerId, matchOffer.orderId());
                        return;
                    }

                    matchOffer.confirmByBoormi(LocalDateTime.now(clock)); // 부르미까지 수락 완료
                    findOrderOfferGroup(matchOffer.orderId())
                            .ifPresentOrElse(
                                    group -> {
                                        group.confirmMatch();
                                        proceedToDelivery(matchOffer, group.boormiId());
                                        cleanUpAfterMatched(matchOffer, group);
                                    },
                                    () -> log.warn("부르미 수락 처리 중 주문 제안 그룹을 찾을 수 없어 배달을 시작하지 못함: offerId={}, orderId={}",
                                            matchOffer.offerId(), matchOffer.orderId())
                            );
                },
                () -> log.debug("존재하지 않는 제안 부르미 수락 요청, 무시: offerId={}", offerId)
        );
    }

    void applyRejectByBoormi(UUID offerId) {
        log.debug("부르미 거절 액션 실행: offerId={}", offerId);

        // 해당 match가 PENDING_BOORMI_CONFIRMATION 상태가 아니라면 이미 수락/만료/취소 등으로 처리가 된거임
        findOffer(offerId)
                .filter(matchOffer -> matchOffer.status() == MatchOfferStatus.PENDING_BOORMI_CONFIRMATION)
                .ifPresentOrElse(
                        matchOffer -> {
                            matchOffer.rejectByBoormi(LocalDateTime.now(clock));

                            // 거절당한 드리미에게 부르미가 거절했음을 알리고, 다시 배달가능 상태로 변경
                            notificationService.notify(matchOffer.dreamiId(), MatchingEventType.BOORMI_REJECTED,
                                    new BoormiRejectedPayload(matchOffer.offerId(), matchOffer.orderId()));
                            releaseDreami(matchOffer.dreamiId());

                            moveGroupToWaitingIfExhausted(matchOffer.orderId());
                        },
                        () -> log.debug("거절 가능한 상태가 아닌 제안 부르미 거절 요청, 무시: offerId={}", offerId)
                );
    }

    public void expireDreamiOffer(UUID offerId) {
        matchingEngine.submit(new ExpireDreamiOffer(this, offerId));
    }

    /**
     * 지정한 시간(ttl) 뒤에 드리미 응답 timeout을 한 번 예약한다. {@link ExpireDreamiOffer}가 이 패키지 안에서만 접근 가능한 record이므로, 다른
     * 패키지({@link MatchingPlanApplier})가 오퍼 timeout을 예약하려면 이 메서드를 거쳐야 한다.
     */
    public void scheduleDreamiOfferTimeout(UUID offerId, Duration ttl) {
        matchingEngine.schedule(new ExpireDreamiOffer(this, offerId), ttl);
    }

    void applyExpireDreamiOffer(UUID offerId) {
        log.debug("드리미 응답시간 만료 액션 실행: offerId={}", offerId);

        // 해당 match가 OFFERED 상태가 아니라면 이미 수락/거절/회수 등으로 처리가 끝난 것이므로, 이 timeout으로는 이벤트를 중복 발송하지 않는다.
        findOffer(offerId)
                .filter(matchOffer -> matchOffer.status() == MatchOfferStatus.OFFERED)
                .ifPresent(matchOffer -> {
                    matchOffer.expireByDreami(LocalDateTime.now(clock));
                    releaseDreami(matchOffer.dreamiId());
                    moveGroupToWaitingIfExhausted(matchOffer.orderId());
                    notificationService.notify(matchOffer.dreamiId(), MatchingEventType.OFFER_CLOSED,
                            new OfferClosedPayload(matchOffer.offerId(), "응답 시간 초과"));
                });
    }

    public void expireBoormiOffer(UUID offerId) {
        matchingEngine.submit(new ExpireBoormiOffer(this, offerId));
    }

    void applyExpireBoormiOffer(UUID offerId) {
        log.debug("부르미 응답시간 만료 액션 실행: offerId={}", offerId);

        // 해당 match가 PENDING_BOORMI_CONFIRMATION 상태가 아니라면 이미 수락/거절/취소 등으로 처리가 된거임
        findOffer(offerId)
                .filter(matchOffer -> matchOffer.status() == MatchOfferStatus.PENDING_BOORMI_CONFIRMATION)
                .ifPresent(matchOffer -> {
                    // DB 주문 잠금(OrderRepository.findByOrderId의 PESSIMISTIC_WRITE)을 먼저 확보해, 그 사이
                    // 부르미가 이미 확정/거절해 DB가 더 이상 이 드리미의 PENDING_BOORMI_CONFIRMATION이 아니게
                    // 됐으면(경합 패배) 인메모리 상태를 전혀 건드리지 않는다 — DB가 이겼으므로 그 결과를 그대로 둔다.
                    boolean expired = boormiOfferExpirationService.expire(matchOffer.orderId(), matchOffer.dreamiId());
                    if (!expired) {
                        log.debug("DB 주문이 이미 다른 경로로 처리되어 부르미 timeout을 무시함: orderId={}, dreamiId={}",
                                matchOffer.orderId(), matchOffer.dreamiId());
                        return;
                    }

                    // 드리미가 다시 배달이 가능하게 바꿔야함
                    matchOffer.expireByBoormi(LocalDateTime.now(clock));
                    releaseDreami(matchOffer.dreamiId());
                    moveGroupToWaitingIfExhausted(matchOffer.orderId());

                    notificationService.notify(matchOffer.dreamiId(), MatchingEventType.OFFER_CLOSED,
                            new OfferClosedPayload(matchOffer.offerId(), "부르미 응답 시간이 만료됐어요."));
                });
    }

    /**
     * 해당 주문에 진행 중(WAITING/OPEN)인 방이 이미 있는지 확인한다. 주문 접수 시 중복 매칭 시작을 트랜잭션 안에서 걸러내는 데도 쓴다.
     */
    public boolean isActiveGroupExists(UUID orderId) {
        OrderOfferGroup existingGroup = orderOfferGroupsByOrderId.get(orderId);
        return existingGroup != null && existingGroup.isActive();
    }

    private Optional<MatchOffer> findOffer(UUID offerId) {
        return Optional.ofNullable(offersById.get(offerId));
    }

    /**
     * offerId 로 확정 대상 드리미를 조회한다. 부르미 확정 시 ORDERS.dreami_id 반영에 사용한다. 해당 오퍼가 없으면 empty.
     */
    public Optional<UUID> findDreamiIdByOfferId(UUID offerId) {
        return findOffer(offerId).map(MatchOffer::dreamiId);
    }

    /**
     * offerId 로 해당 제안이 속한 주문을 조회한다. 드리미 수락 시 ORDERS.order_cd 반영에 사용한다. 해당 오퍼가 없으면 empty.
     */
    public Optional<UUID> findOrderIdByOfferId(UUID offerId) {
        return findOffer(offerId).map(MatchOffer::orderId);
    }

    public Optional<OrderOfferGroup> findOrderOfferGroup(UUID orderId) {
        return Optional.ofNullable(orderOfferGroupsByOrderId.get(orderId));
    }

    /**
     * 해당 제안이 주어진 드리미에게 온 것인지 확인한다. 제안이 존재하지 않으면 false.
     *
     * @param offerId  확인할 제안 UUID
     * @param dreamiId 요청한 드리미 UUID
     * @return 제안의 대상 드리미가 dreamiId와 일치하면 true
     */
    public boolean isDreamiOfferOwner(UUID offerId, UUID dreamiId) {
        return findOffer(offerId).map(offer -> offer.dreamiId().equals(dreamiId)).orElse(false);
    }

    /**
     * 해당 제안이 지금 이 드리미가 실제로 수락 가능한 상태인지 확인한다. {@code isDreamiOfferOwner}는 대상 드리미 일치만 보므로,
     * 엔진이 이미 timeout으로 DREAMI_EXPIRED 처리한 오래된 offerId로도 통과해버린다. DB 전이(주문 상태 변경, 이벤트 발행)를 하기 전에
     * 상태(OFFERED)와 TTL 경과 여부까지 함께 검증해, 이미 만료된 제안의 수락을 커밋 전에 차단한다.
     *
     * @param offerId  확인할 제안 UUID
     * @param dreamiId 요청한 드리미 UUID
     * @return 제안이 존재하고, dreamiId가 일치하고, 상태가 OFFERED이고, TTL이 아직 지나지 않았으면 true
     */
    public boolean isDreamiOfferAcceptable(UUID offerId, UUID dreamiId) {
        return findOffer(offerId)
                .filter(offer -> offer.dreamiId().equals(dreamiId))
                .filter(offer -> offer.status() == MatchOfferStatus.OFFERED)
                .filter(offer -> offer.statusUpdatedAt().plus(OFFER_TTL).isAfter(LocalDateTime.now(clock)))
                .isPresent();
    }

    /**
     * 해당 제안이 속한 주문이 주어진 부르미의 것인지 확인한다. 제안이나 방이 존재하지 않으면 false.
     *
     * @param offerId  확인할 제안 UUID
     * @param boormiId 요청한 부르미 UUID
     * @return 제안이 속한 방의 부르미가 boormiId와 일치하면 true
     */
    public boolean isBoormiOfferOwner(UUID offerId, UUID boormiId) {
        return findOffer(offerId)
                .flatMap(offer -> findOrderOfferGroup(offer.orderId()))
                .map(group -> group.boormiId().equals(boormiId))
                .orElse(false);
    }

    /**
     * 해당 드리미가 응답 대기 중(OFFERED)인 제안을 조회한다. 한 드리미는 동시에 하나의 오퍼만 응답 대기 상태로 가질 수 있으므로 첫 건만 반환한다. 거절/만료 등으로 종료된 제안은 대상이 아니다.
     */
    public Optional<MatchOffer> findPendingOfferForDreami(UUID dreamiId) {
        return offersById.values().stream()
                .filter(offer -> offer.dreamiId().equals(dreamiId) && offer.status() == MatchOfferStatus.OFFERED)
                .findFirst();
    }

    /**
     * 드리미 응답 대기 오퍼의 TTL. 컨트롤러가 {@code PendingOfferDto}의 expiresAt을 복원할 때 팝업 발송 시와 같은 값을 쓰기 위해 노출한다.
     */
    public Duration offerTtl() {
        return OFFER_TTL;
    }

    /**
     * 부르미 확인 대기 오퍼의 TTL. 컨트롤러가 {@code DreamiInfoPayload}의 expiresAt을 복원할 때 팝업 발송 시와 같은 값을 쓰기 위해 노출한다.
     */
    public Duration boormiOfferTtl() {
        return BOORMI_OFFER_TTL;
    }

    /**
     * 해당 부르미의 주문 중, 드리미가 수락해 확인 대기 중(PENDING_BOORMI_CONFIRMATION)인 제안을 조회한다. 종료된 제안은 대상이 아니다.
     */
    public Optional<MatchOffer> findIncomingDreamiOffer(UUID boormiId) {
        return orderOfferGroupsByOrderId.values().stream()
                .filter(group -> group.boormiId().equals(boormiId))
                .flatMap(group -> group.offers().stream())
                .filter(offer -> offer.status() == MatchOfferStatus.PENDING_BOORMI_CONFIRMATION)
                .findFirst();
    }

    /**
     * 오퍼의 드리미-픽업지 간 직선거리를 도보 속도로 환산한 예상 픽업 시간(분). 실시간 경로가 아닌 추정치이며, 방/드리미 위치를 알 수 없으면(이미 정리되었거나 위치 정보가 없으면) null을
     * 반환한다.
     */
    public Integer pickupEtaMinutesForOffer(MatchOffer offer) {
        OrderOfferGroup group = orderOfferGroupsByOrderId.get(offer.orderId());
        WaitingDreami dreami = dreamiMap.get(offer.dreamiId());
        if (group == null || dreami == null || !hasCoordinates(group.location()) || !hasCoordinates(
                dreami.location())) {
            return null;
        }
        double distanceMeters = geoDistanceCalculator.distanceMeters(group.location(), dreami.location());
        return PickupEtaCalculator.minutesFromDistance(distanceMeters);
    }

    private boolean hasCoordinates(GeoPoint point) {
        return point != null && point.latitude() != null && point.longitude() != null;
    }

    /**
     * 드리미를 다시 매칭 대기(MATCHING) 상태로 되돌린다. 이미 등록이 해제됐거나 이미 MATCHING이면(중복 처리) 아무 것도 바뀌지 않는다.
     */
    private void releaseDreami(UUID dreamiId) {
        WaitingDreami dreami = dreamiMap.get(dreamiId);
        if (dreami == null || dreami.status() == WaitingDreamiStatus.MATCHING) {
            return;
        }
        dreami.markMatching();
    }

    /**
     * 방 안의 모든 오퍼가 거절/만료/철회로 끝나 더 이상 진행 중인 오퍼가 없으면, 다음 batch를 기다리는 WAITING 상태로 되돌린다. 그룹이 존재하지 않거나 이미 OPEN이
     * 아니거나(WAITING/MATCHED/CANCELLED) 아직 살아 있는 오퍼가 남아 있으면 아무 것도 바뀌지 않는다.
     *
     * @return 실제로 WAITING으로 전이됐으면 true
     */
    private boolean moveGroupToWaitingIfExhausted(UUID orderId) {
        return findOrderOfferGroup(orderId)
                .filter(group -> group.status() == OrderOfferGroupStatus.OPEN)
                .filter(group -> group.offers().stream()
                        .noneMatch(offer -> offer.status() == MatchOfferStatus.OFFERED
                                || offer.status() == MatchOfferStatus.PENDING_BOORMI_CONFIRMATION
                                || offer.status() == MatchOfferStatus.MATCHED))
                .map(group -> {
                    group.closeForRematch();
                    return true;
                })
                .orElse(false);
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
            notificationService.notify(offer.dreamiId(), MatchingEventType.OFFER_ERROR,
                    new NotificationErrorPayload(offer.offerId(), "이미 다른 드리미가 수락한 주문입니다."));
            return Optional.empty();
        }
        // 정상적으로 수락 가능한 상태는 OFFERED 뿐. (거절/만료된 제안은 수락 불가)
        if (offer.status() != MatchOfferStatus.OFFERED) {
            notificationService.notify(offer.dreamiId(), MatchingEventType.OFFER_ERROR,
                    new NotificationErrorPayload(offer.offerId(), "이미 종료된 제안입니다."));
            return Optional.empty();
        }
        return Optional.of(offer);
    }

    /**
     * 매칭이 성사된 후 인메모리의 매칭 정보를 삭제한다.
     *
     * @param matchOffer 해당하는 offer
     * @param group      모든 offer들을 담은 그룹
     */
    private void cleanUpAfterMatched(MatchOffer matchOffer, OrderOfferGroup group) {
        dreamiMap.remove(matchOffer.dreamiId());

        // 상태에 관계 없이 삭제를 진행한다.
        for (MatchOffer offer : group.offers()) {
            offersById.remove(offer.offerId());
        }
        orderOfferGroupsByOrderId.remove(group.orderId());
    }
    // ────────────────────────────── 배달 연동 ──────────────────────────────

    private void proceedToDelivery(MatchOffer matchOffer, UUID boormiId) {
        deliveryService.startDelivery(matchOffer.orderId(), matchOffer.dreamiId(), boormiId);
    }
}

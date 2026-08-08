package com.naengsam.quick.domain.dreami.service;

import com.naengsam.quick.domain.boormi.entity.Boormi;
import com.naengsam.quick.domain.boormi.repository.BoormiRepository;
import com.naengsam.quick.domain.delivery.entity.Delivery;
import com.naengsam.quick.domain.delivery.repository.DeliveryRepository;
import com.naengsam.quick.domain.dreami.dto.DreamiDashboardDto;
import com.naengsam.quick.domain.dreami.dto.DreamiDeliveryHistoryDto;
import com.naengsam.quick.domain.dreami.dto.DreamiDeliveryHistoryResponse;
import com.naengsam.quick.domain.dreami.dto.DreamiProfileDto;
import com.naengsam.quick.domain.dreami.dto.MonthlyRevenueDto;
import com.naengsam.quick.domain.dreami.dto.NearbyCallDto;
import com.naengsam.quick.domain.dreami.entity.Dreami;
import com.naengsam.quick.domain.dreami.entity.DreamiCd;
import com.naengsam.quick.domain.dreami.entity.DreamiReview;
import com.naengsam.quick.domain.dreami.exception.DreamiErrorCode;
import com.naengsam.quick.domain.dreami.repository.DreamiRepository;
import com.naengsam.quick.domain.dreami.repository.DreamiRequestDeniedDetailsRepository;
import com.naengsam.quick.domain.dreami.repository.DreamiReviewRepository;
import com.naengsam.quick.domain.matching.dto.GeoPoint;
import com.naengsam.quick.domain.matching.dto.NearbyOrderDto;
import com.naengsam.quick.domain.matching.dto.NearbyOrderRequest;
import com.naengsam.quick.domain.matching.event.DreamiAcceptedEvent;
import com.naengsam.quick.domain.matching.exception.MatchingErrorCode;
import com.naengsam.quick.domain.matching.service.MatchingService;
import com.naengsam.quick.domain.matching.service.NearbyOrderFinder;
import com.naengsam.quick.domain.order.dto.OrderCursor;
import com.naengsam.quick.domain.order.dto.OrderSummaryDto;
import com.naengsam.quick.domain.order.entity.OrderCd;
import com.naengsam.quick.domain.order.entity.Orders;
import com.naengsam.quick.domain.order.exception.OrderErrorCode;
import com.naengsam.quick.domain.order.repository.OrderRepository;
import com.naengsam.quick.domain.payment.dto.MonthlyMoneyAggregate;
import com.naengsam.quick.domain.payment.entity.MoneyTxStatusCd;
import com.naengsam.quick.domain.payment.entity.MoneyTxTypeCd;
import com.naengsam.quick.domain.payment.repository.MoneyTxRepository;
import com.naengsam.quick.global.exception.BusinessException;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DreamiService {

    private static final int MIN_PAGE_SIZE = 1;
    private static final int MAX_PAGE_SIZE = 100;

    private final DreamiRepository dreamiRepository;
    private final BoormiRepository boormiRepository;
    private final DreamiRequestDeniedDetailsRepository dreamiRequestDeniedDetailsRepository;
    private final DreamiReviewRepository dreamiReviewRepository;
    private final OrderRepository orderRepository;
    private final DeliveryRepository deliveryRepository;
    private final NearbyOrderFinder nearbyOrderFinder;
    private final MatchingService matchingService;
    private final MoneyTxRepository moneyTxRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 저장 직전에 락 걸린 조회로 승인 여부를 다시 확인한다. {@code assertNotAlreadyApproved}로 미리 확인했더라도, 그 뒤 저장
     * 시점 사이에 어드민이 승인을 확정할 수 있으므로 이 재확인이 실제 안전장치다.
     */
    @Transactional
    public void saveVerificationFileKeys(UUID dreamiId, String idCardKey, String criminalRecordKey) {
        dreamiRepository.findByDreamiId(dreamiId).ifPresent(this::throwIfApproved);
        dreamiRepository.save(Dreami.create(dreamiId, idCardKey, criminalRecordKey));
    }

    /**
     * 이미 승인된 드리미는 재신청할 수 없다. {@code saveVerificationFileKeys}가 매번 새 row로 덮어써서
     * requestCd·평점을 리셋시키므로, 승인된 드리미가 이 흐름을 다시 타는 것을 여기서 막는다. 락 없이 빠르게 걷어내는
     * 사전 체크용이고, 실제 안전장치는 {@code saveVerificationFileKeys} 내부의 락 걸린 재확인이다.
     */
    @Transactional(readOnly = true)
    public void assertNotAlreadyApproved(UUID dreamiId) {
        dreamiRepository.findById(dreamiId).ifPresent(this::throwIfApproved);
    }

    private void throwIfApproved(Dreami dreami) {
        if (dreami.getRequestCd() == DreamiCd.APPROVED) {
            throw new BusinessException(DreamiErrorCode.ALREADY_APPROVED);
        }
    }

    /**
     * 드리미를 온라인 상태로 전환한다. 승인된 드리미만 가능하며, 본인이 드리미/부르미 어느 역할로든 수행 중인 주문이 있으면(dreami_id == boormi_id) 온라인 전환할 수 없다.
     */
    @Transactional(readOnly = true)
    public void goOnline(UUID dreamiId, GeoPoint location) {
        Dreami dreami = dreamiRepository.findById(dreamiId)
                .orElseThrow(() -> new BusinessException(DreamiErrorCode.NOT_FOUND));
        if (dreami.getRequestCd() != DreamiCd.APPROVED) {
            throw new BusinessException(DreamiErrorCode.NOT_APPROVED);
        }
        if (orderRepository.countActiveOrders(dreamiId) > 0) {
            throw new BusinessException(DreamiErrorCode.ALREADY_HAS_ACTIVE_ORDER);
        }

        matchingService.registerDreami(dreamiId, location);
    }

    /**
     * 드리미가 제안을 수락한다. 매칭엔진에 반영하기 전에 주문을 PENDING_BOORMI_CONFIRMATION으로 전이해 DB에도 반영한다. 매칭엔진 제출은 이 트랜잭션이 커밋된 뒤에
     * 일어난다.
     */
    @Transactional
    public void acceptOffer(UUID offerId, UUID dreamiId) {
        if (!matchingService.isDreamiOfferOwner(offerId, dreamiId)) {
            throw new BusinessException(MatchingErrorCode.NOT_OFFER_OWNER);
        }
        UUID orderId = matchingService.findOrderIdByOfferId(offerId)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
        Orders order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));

        order.markPendingBoormiConfirmation();

        // 엔진은 수락 즉시 부르미에게 확인 팝업을 보내고, 부르미의 확정은 주문이 PENDING_BOORMI_CONFIRMATION 인지
        // 검사하므로 커밋 후에 제출해야 한다. 커밋 후 처리는 MatchingService 의 리스너가 담당한다.
        eventPublisher.publishEvent(new DreamiAcceptedEvent(offerId));
    }

    /**
     * 드리미가 제안을 거절한다. 주문은 계속 매칭 대기 상태이므로 DB 변경은 필요 없다.
     */
    @Transactional
    public void rejectOffer(UUID offerId, UUID dreamiId) {
        if (!matchingService.isDreamiOfferOwner(offerId, dreamiId)) {
            throw new BusinessException(MatchingErrorCode.NOT_OFFER_OWNER);
        }
        matchingService.rejectByDreami(offerId);
    }

    /**
     * 부르미가 드리미 프로필을 조회한다. dreamiId 는 boormiId 와 동일한 값이므로 이름은 BOORMI 테이블에서 가져온다.
     */
    @Transactional(readOnly = true)
    public DreamiProfileDto getDreamiProfile(UUID dreamiId) {
        Dreami dreami = dreamiRepository.findById(dreamiId)
                .orElseThrow(() -> new BusinessException(DreamiErrorCode.NOT_FOUND));
        Boormi boormi = boormiRepository.findById(dreamiId)
                .orElseThrow(() -> new BusinessException(DreamiErrorCode.NOT_FOUND));
        long rejectCount = dreamiRequestDeniedDetailsRepository.countByDreamiId(dreamiId);

        return DreamiProfileDto.from(dreami, boormi.getName(), rejectCount);
    }

    /**
     * 드리미가 현재 수행 중인 배달 건의 카드 정보를 조회한다. 드리미는 한 번에 하나만 수행하므로 단건 조회다.
     * 진행 중인 배달이 없으면 정상 상태이므로 예외 대신 null을 반환한다.
     */
    @Transactional(readOnly = true)
    public OrderSummaryDto findCurrentDeliveryCard(UUID dreamiId) {
        return orderRepository.findByDreamiIdAndOrderCd(dreamiId, OrderCd.IN_PROGRESS)
                .map(OrderSummaryDto::from)
                .orElse(null);
    }

    /**
     * 드리미가 지정한 좌표 반경 내에 열려있는 콜(주문)을 거리순으로 조회한다. 각 콜에 예상 수익/소요시간을 함께 담아 반환한다.
     */
    @Transactional(readOnly = true)
    public List<NearbyCallDto> findNearbyCalls(NearbyOrderRequest request) {
        return nearbyOrderFinder.find(request).stream()
                .map(this::toNearbyCallDto)
                .toList();
    }

    /**
     * matching이 돌려준 건 위치/거리뿐이라, 화면에 보여줄 품목/예상수익/ETA는 order 도메인에서 주문을 다시 조회해 채운다. 방금 nearbyOrderFinder가 찾아준 주문이라 사실상 항상
     * 존재한다.
     */
    private NearbyCallDto toNearbyCallDto(NearbyOrderDto nearby) {
        Orders order = orderRepository.findById(nearby.orderId())
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
        return NearbyCallDto.from(nearby, order);
    }

    /**
     * 드리미 대시보드 — 완료 건수, 이번 달 수익, 지난달 대비 증감률, 시장 평균 단가 대비 초과 수익, 최근 6개월 수익 추이를 조회한다. 증감률은 지난달 수익이 0이면 0%로 처리하고, 반올림해 소수점
     * 없이 반환한다.
     */
    @Transactional(readOnly = true)
    public DreamiDashboardDto getDashboard(UUID dreamiId) {
        long completedCount = orderRepository.countByDreamiIdAndOrderCd(dreamiId, OrderCd.COMPLETED);

        YearMonth thisMonth = YearMonth.now();
        YearMonth rangeStart = thisMonth.minusMonths(5);
        // WALLET 은 boormi_id 로 소유자를 식별하며, dreamiId 는 boormiId 와 동일한 값이다.
        Map<YearMonth, MonthlyMoneyAggregate> byMonth = moneyTxRepository
                .aggregateByBoormiIdAndTypeBetween(dreamiId, MoneyTxTypeCd.SETTLEMENT, MoneyTxStatusCd.SETTLED,
                        rangeStart.atDay(1).atStartOfDay(), thisMonth.plusMonths(1).atDay(1).atStartOfDay())
                .stream()
                .collect(Collectors.toMap(MonthlyMoneyAggregate::yearMonth, aggregate -> aggregate));

        long thisMonthRevenue = amountOf(byMonth, thisMonth);
        long lastMonthRevenue = amountOf(byMonth, thisMonth.minusMonths(1));
        long growthPercent = lastMonthRevenue == 0 ? 0
                : Math.round((thisMonthRevenue - lastMonthRevenue) * 100.0 / lastMonthRevenue);

        long thisMonthCount = countOf(byMonth, thisMonth);

        List<MonthlyRevenueDto> recentSixMonths = IntStream.rangeClosed(0, 5)
                .mapToObj(thisMonth::minusMonths)
                .sorted()
                .map(month -> new MonthlyRevenueDto(month, amountOf(byMonth, month)))
                .toList();

        return DreamiDashboardDto.of(completedCount, thisMonthRevenue, growthPercent,
                thisMonthCount, recentSixMonths);
    }

    private long amountOf(Map<YearMonth, MonthlyMoneyAggregate> byMonth, YearMonth month) {
        MonthlyMoneyAggregate aggregate = byMonth.get(month);
        return aggregate == null ? 0 : aggregate.totalAmount();
    }

    private long countOf(Map<YearMonth, MonthlyMoneyAggregate> byMonth, YearMonth month) {
        MonthlyMoneyAggregate aggregate = byMonth.get(month);
        return aggregate == null ? 0 : aggregate.count();
    }

    /**
     * 드리미 활동 내역을 최신순 커서 페이지네이션으로 조회한다. 다음 페이지 존재 여부는 size+1 개를 조회해 판단하고, 초과분은 잘라낸 뒤 마지막 항목으로 다음 커서를 만든다.
     * 배달 상태·완료 시각(Delivery)·평점(DreamiReview)은 이번 페이지 주문들의 orderId로 한 번에 배치 조회해 N+1 쿼리를 피한다.
     */
    @Transactional(readOnly = true)
    public DreamiDeliveryHistoryResponse getDeliveryHistory(UUID dreamiId, String cursor, int size) {
        // size가 MIN_PAGE_SIZE보다 작으면 size=MIN_PAGE_SIZE
        // size가 MAX_PAGE_SIZE보다 크면 size=MAX_PAGE_SIZE
        int pageSize = Math.clamp(size, MIN_PAGE_SIZE, MAX_PAGE_SIZE);

        List<Orders> rows;
        if (cursor == null) { // 첫번째 페이지면
            rows = orderRepository.findFirstPageByDreami(dreamiId, pageSize + 1); // size보다 하나 더 요청해서 다음 읽을 게 있는지 확인
        } else {
            OrderCursor decoded = OrderCursor.decode(cursor);
            rows = orderRepository.findPageByDreamiAfterCursor(
                    dreamiId, decoded.dtm(), decoded.orderId(), pageSize + 1);
        }

        boolean hasNext = rows.size() > pageSize;
        List<Orders> page = hasNext ? rows.subList(0, pageSize) : rows; // 아까 위에서 하나 더 요청했기 때문에 이거는 버려야 함

        String nextCursor = null;
        if (hasNext) {
            Orders last = page.getLast();
            nextCursor = new OrderCursor(last.getDeliveryRequestDtm(), last.getOrderId()).encode();
        }

        // 이번 페이지에 보여줄 주문들의 orderId만 추출
        List<UUID> orderIds = page.stream().map(Orders::getOrderId).toList();

        // 배치 조회를 통해 쿼리 하나로 20개 주문의 Delivery 객체 가져옴
        Map<UUID, Delivery> deliveryByOrderId = deliveryRepository.findAllByOrderIdIn(orderIds).stream()
                .collect(Collectors.toMap(Delivery::getOrderId, delivery -> delivery)); // key: orderId, value: delivery 객체
        Map<UUID, DreamiReview> reviewByOrderId = dreamiReviewRepository.findAllByOrderIdIn(orderIds).stream()
                .collect(Collectors.toMap(DreamiReview::getOrderId, review -> review));

        // 주문 20개를 다시 순회하면서 dto 생성
        List<DreamiDeliveryHistoryDto> deliveries = page.stream()
                .map(order -> DreamiDeliveryHistoryDto.of(order, deliveryByOrderId.get(order.getOrderId()),
                        reviewByOrderId.get(order.getOrderId())))
                .toList();

        return DreamiDeliveryHistoryResponse.of(deliveries, nextCursor, hasNext);
    }
}

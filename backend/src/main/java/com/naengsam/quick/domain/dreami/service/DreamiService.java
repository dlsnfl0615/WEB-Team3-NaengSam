package com.naengsam.quick.domain.dreami.service;

import com.naengsam.quick.domain.boormi.entity.Boormi;
import com.naengsam.quick.domain.boormi.repository.BoormiRepository;
import com.naengsam.quick.domain.delivery.repository.DeliveryRepository;
import com.naengsam.quick.domain.dreami.dto.DreamiDashboardDto;
import com.naengsam.quick.domain.dreami.dto.DreamiProfileDto;
import com.naengsam.quick.domain.dreami.dto.DreamiTodayStatsDto;
import com.naengsam.quick.domain.dreami.dto.MonthlyRevenueDto;
import com.naengsam.quick.domain.dreami.dto.DreamiReviewDto;
import com.naengsam.quick.domain.dreami.dto.NearbyCallDto;
import com.naengsam.quick.domain.dreami.entity.Dreami;
import com.naengsam.quick.domain.dreami.entity.DreamiCd;
import com.naengsam.quick.domain.dreami.entity.DreamiRequestDeniedDetails;
import com.naengsam.quick.domain.dreami.exception.DreamiErrorCode;
import com.naengsam.quick.domain.dreami.repository.DreamiRepository;
import com.naengsam.quick.domain.dreami.repository.DreamiRequestDeniedDetailsRepository;
import com.naengsam.quick.domain.matching.dto.GeoPoint;
import com.naengsam.quick.domain.matching.dto.NearbyOrderDto;
import com.naengsam.quick.domain.matching.dto.NearbyOrderRequest;
import com.naengsam.quick.domain.matching.event.DreamiAcceptedEvent;
import com.naengsam.quick.domain.matching.exception.MatchingErrorCode;
import com.naengsam.quick.domain.matching.service.MatchingService;
import com.naengsam.quick.domain.matching.service.NearbyOrderFinder;
import com.naengsam.quick.domain.order.dto.BoormiOrdersResponse;
import com.naengsam.quick.domain.order.dto.OrderCountDto;
import com.naengsam.quick.domain.order.dto.OrderSummaryDto;
import com.naengsam.quick.domain.order.entity.OrderCd;
import com.naengsam.quick.domain.order.entity.Orders;
import com.naengsam.quick.domain.order.entity.Role;
import com.naengsam.quick.domain.order.exception.OrderErrorCode;
import com.naengsam.quick.domain.order.repository.OrderRepository;
import com.naengsam.quick.domain.order.service.OrderService;
import com.naengsam.quick.domain.payment.dto.MonthlyMoneyAggregate;
import com.naengsam.quick.domain.payment.entity.MoneyTxStatusCd;
import com.naengsam.quick.domain.payment.entity.MoneyTxTypeCd;
import com.naengsam.quick.domain.payment.repository.MoneyTxRepository;
import com.naengsam.quick.domain.upload.service.S3PresignService;
import com.naengsam.quick.global.exception.BusinessException;
import java.time.LocalDate;
import java.time.LocalDateTime;
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

    private final DreamiRepository dreamiRepository;
    private final BoormiRepository boormiRepository;
    private final DreamiRequestDeniedDetailsRepository dreamiRequestDeniedDetailsRepository;
    private final OrderRepository orderRepository;
    private final DeliveryRepository deliveryRepository;
    private final OrderService orderService;
    private final NearbyOrderFinder nearbyOrderFinder;
    private final MatchingService matchingService;
    private final S3PresignService s3PresignService;
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
        Orders order = orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));

        // 락을 잡은 뒤 재확인 — 다른 드리미가 먼저 커밋해 이미 MATCHING이 아니게 됐을 수 있다.
        if (order.getOrderCd() == OrderCd.PENDING_BOORMI_CONFIRMATION) {
            if (dreamiId.equals(order.getDreamiId())) {
                // 본인이 이미 성공시킨 수락의 재시도(더블클릭/네트워크 재시도) — 멱등하게 조용히 반환한다.
                return;
            }
            throw new BusinessException(MatchingErrorCode.ALREADY_ACCEPTED_BY_OTHER);
        }

        if (order.getOrderCd() != OrderCd.MATCHING) {
            throw new BusinessException(MatchingErrorCode.NOT_ACCEPTABLE_STATUS);
        }

        order.markPendingBoormiConfirmation(dreamiId);

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

    /**
     * 드리미가 수행한(수행 중인) 배달을 최신순 커서 페이지네이션으로 조회한다. status 로 단일 상태 필터링이 가능하다.
     */
    @Transactional(readOnly = true)
    public BoormiOrdersResponse getMyOrders(UUID dreamiId, String cursor, int size, OrderCd status) {
        return orderService.getOrders(dreamiId, Role.DREAMI, cursor, size, status);
    }

    /**
     * 배달 하나를 주문 id로 직접 조회한다. 활동 내역 상세 화면이 목록 페이지네이션(getMyOrders)과 무관하게
     * 딥링크/새로고침으로 바로 들어왔을 때, 그 배달 하나만 정확히 찾기 위해 쓴다.
     */
    @Transactional(readOnly = true)
    public OrderSummaryDto getMyDelivery(UUID dreamiId, UUID orderId) {
        Orders order = orderService.getOrder(orderId);
        if (!dreamiId.equals(order.getDreamiId())) {
            throw new BusinessException(OrderErrorCode.NOT_ORDER_OWNER);
        }
        return OrderSummaryDto.from(order);
    }

    /**
     * 활동 내역 화면의 "총 N건" 표시용 전체 배달 건수(상태 무관). 목록은 페이지네이션으로 일부만 들고 있어
     * records.length 로는 실제 총 건수를 알 수 없어서 별도로 집계한다.
     */
    @Transactional(readOnly = true)
    public OrderCountDto getMyDeliveryCount(UUID dreamiId) {
        return OrderCountDto.of(orderRepository.countByDreamiId(dreamiId));
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
     * 홈 화면의 "오늘의 수익"/"완료 건수" — 오늘 하루 스코프로 집계한다. 오늘의 수익은 정산(SETTLEMENT) 중 이미
     * 확정(SETTLED)된 금액만 센다. 완료 건수는 오늘 배달 완료(markDelivered) 처리된 건수다.
     */
    @Transactional(readOnly = true)
    public DreamiTodayStatsDto getTodayStats(UUID dreamiId) {
        LocalDate today = LocalDate.now();
        LocalDateTime start = today.atStartOfDay();
        LocalDateTime end = today.plusDays(1).atStartOfDay();

        // WALLET 은 boormi_id 로 소유자를 식별하며, dreamiId 는 boormiId 와 동일한 값이다.
        long todayRevenue = moneyTxRepository
                .aggregateByBoormiIdAndTypeBetween(
                        dreamiId, MoneyTxTypeCd.SETTLEMENT, MoneyTxStatusCd.SETTLED, start, end)
                .stream()
                .mapToLong(MonthlyMoneyAggregate::totalAmount)
                .sum();

        long todayCompletedCount = deliveryRepository.countDeliveredBetween(dreamiId, start, end);

        return DreamiTodayStatsDto.of(todayRevenue, todayCompletedCount);
    }

    /**
     * 관리자 검수 대기 중(REQUESTED)인 드리미 인증 신청 목록을 조회한다. 신분증/범죄경력 사진은 presigned 다운로드 URL로 변환해 담는다.
     */
    @Transactional(readOnly = true)
    public List<DreamiReviewDto> listPendingReviews() {
        return dreamiRepository.findAllByRequestCd(DreamiCd.REQUESTED).stream()
                .map(dreami -> DreamiReviewDto.of(dreami,
                        s3PresignService.generateDownloadUrl(dreami.getIdCardKey()),
                        s3PresignService.generateDownloadUrl(dreami.getCriminalRecordKey())))
                .toList();
    }

    /**
     * 관리자가 드리미 인증 신청을 승인한다.
     */
    @Transactional
    public void approveReview(UUID dreamiId) {
        Dreami dreami = dreamiRepository.findByDreamiId(dreamiId)
                .orElseThrow(() -> new BusinessException(DreamiErrorCode.NOT_FOUND));
        dreami.approve();

        Boormi boormi = boormiRepository.findById(dreamiId)
                .orElseThrow(() -> new BusinessException(DreamiErrorCode.NOT_FOUND));
        boormi.approve();
    }

    /**
     * 관리자가 드리미 인증 신청을 반려한다. 반려 사유는 드리미 프로필 조회에 쓰이는 반려 이력 테이블에도 함께 남긴다.
     */
    @Transactional
    public void rejectReview(UUID dreamiId, String reason) {
        Dreami dreami = dreamiRepository.findByDreamiId(dreamiId)
                .orElseThrow(() -> new BusinessException(DreamiErrorCode.NOT_FOUND));
        dreami.reject(reason);
        dreamiRequestDeniedDetailsRepository.save(DreamiRequestDeniedDetails.create(dreamiId, reason));
    }
}

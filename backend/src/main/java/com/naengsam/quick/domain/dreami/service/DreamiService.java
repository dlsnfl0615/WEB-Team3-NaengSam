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
import com.naengsam.quick.domain.dreami.dto.OfferItemPhotoDto;
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
import com.naengsam.quick.domain.order.dto.NearbyCallOrderDto;
import com.naengsam.quick.domain.order.dto.OrderCountDto;
import com.naengsam.quick.domain.order.dto.OrderStatusCountDto;
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
import com.naengsam.quick.domain.user.service.UserActivityResolver;
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

@Service // 이 클래스가 비즈니스 로직을 담는 Spring 서비스 빈임을 표시(Spring이 관리하는 객체로 등록)
@RequiredArgsConstructor // Lombok: 아래 모든 final 필드를 매개변수로 받는 생성자를 자동 생성 -> Spring이 그 생성자로 의존성을 주입해준다
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
    private final UserActivityResolver userActivityResolver;

    /**
     * 저장 직전에 락 걸린 조회로 승인 여부를 다시 확인한다. {@code assertNotAlreadyApproved}로 미리 확인했더라도, 그 뒤 저장
     * 시점 사이에 어드민이 승인을 확정할 수 있으므로 이 재확인이 실제 안전장치다.
     */
    @Transactional // 이 메서드 전체를 하나의 DB 트랜잭션으로 묶는다(중간에 실패하면 전부 롤백)
    public void saveVerificationFileKeys(UUID dreamiId, String idCardKey, String criminalRecordKey) {
        // findByDreamiId(...)는 Optional<Dreami>를 반환한다. ifPresent(메서드참조)는 "값이 있으면 그 값을 넣어서
        // 이 메서드를 호출하라"는 뜻 — this::throwIfApproved 는 dreami -> this.throwIfApproved(dreami) 람다를 줄여 쓴 메서드 참조 문법.
        dreamiRepository.findByDreamiId(dreamiId).ifPresent(this::throwIfApproved);
        dreamiRepository.save(Dreami.create(dreamiId, idCardKey, criminalRecordKey));
    }

    /**
     * 이미 승인된 드리미는 재신청할 수 없다. {@code saveVerificationFileKeys}가 매번 새 row로 덮어써서
     * requestCd·평점을 리셋시키므로, 승인된 드리미가 이 흐름을 다시 타는 것을 여기서 막는다. 락 없이 빠르게 걷어내는
     * 사전 체크용이고, 실제 안전장치는 {@code saveVerificationFileKeys} 내부의 락 걸린 재확인이다.
     */
    @Transactional(readOnly = true) // 조회만 하는 트랜잭션임을 표시(Hibernate가 변경 감지를 생략해 더 가볍게 동작)
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
     *
     * <p>이미 온라인인 것은 막지 않는다 — 클라이언트의 온라인 상태는 메모리에만 있어 새로고침하면 사라지는데, 여기서 거절해버리면 화면이 오프라인으로 굳은 채 되돌릴 방법이 없어진다.
     * 중복 등록 자체는 {@code registerDreami}가 무시한다.
     */
    @Transactional(readOnly = true)
    public void goOnline(UUID dreamiId, GeoPoint location) {
        // orElseThrow(람다): Optional 안에 값이 있으면 그 값을 꺼내고, 없으면 람다가 만든 예외를 던진다.
        // () -> new BusinessException(...) 은 "인자 없이 실행해서 예외 객체 하나를 만드는" 함수를 의미하는 람다식.
        Dreami dreami = dreamiRepository.findById(dreamiId)
                .orElseThrow(() -> new BusinessException(DreamiErrorCode.NOT_FOUND));
        if (dreami.getRequestCd() != DreamiCd.APPROVED) {
            throw new BusinessException(DreamiErrorCode.NOT_APPROVED);
        }
        if (userActivityResolver.resolve(dreamiId).orderId() != null) {
            throw new BusinessException(DreamiErrorCode.ALREADY_HAS_ACTIVE_ORDER);
        }

        matchingService.registerDreami(dreamiId, location);
    }

    /**
     * 드리미가 제안을 수락한다. 매칭엔진에 반영하기 전에 주문을 PENDING_BOORMI_CONFIRMATION으로 전이해 DB에도 반영한다. 매칭엔진 제출은 이 트랜잭션이 커밋된 뒤에
     * 일어난다.
     *
     * <p>{@code isDreamiOfferOwner}만으로는 부족하다 — 엔진이 이미 timeout으로 DREAMI_EXPIRED 처리한 오래된 offerId도
     * 대상 드리미만 일치하면 통과해버린다. DB를 건드리기 전에 {@code isDreamiOfferAcceptable}로 상태(OFFERED)와 TTL까지 확인해,
     * 이미 만료된 제안이 주문을 잘못 PENDING_BOORMI_CONFIRMATION으로 옮기거나 이벤트를 발행하지 않도록 막는다. 엔진의
     * {@code acceptableOffer} 재검증은 이 검사와 무관하게 최종 방어선으로 유지된다.
     *
     * <p>멱등 재시도 판단도 dreamiId만으로는 부족하다 — 같은 드리미라도 그 사이 새 오퍼(재매칭)로 넘어갔을 수 있어, 실제로
     * 지금 확정 대기 중인 offerId({@code order.getPendingOfferId()})까지 일치해야 "본인의 이전 수락 재시도"로 본다.
     */
    @Transactional
    public void acceptOffer(UUID offerId, UUID dreamiId) {
        if (!matchingService.isDreamiOfferOwner(offerId, dreamiId)) {
            throw new BusinessException(MatchingErrorCode.NOT_OFFER_OWNER);
        }
        if (!matchingService.isDreamiOfferAcceptable(offerId, dreamiId)) {
            throw new BusinessException(MatchingErrorCode.OFFER_EXPIRED);
        }
        UUID orderId = matchingService.findOrderIdByOfferId(offerId)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
        Orders order = orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));

        // 락을 잡은 뒤 재확인 — 다른 드리미가 먼저 커밋해 이미 MATCHING이 아니게 됐을 수 있다.
        if (order.getOrderCd() == OrderCd.PENDING_BOORMI_CONFIRMATION) {
            if (dreamiId.equals(order.getDreamiId()) && offerId.equals(order.getPendingOfferId())) {
                // 본인이 이미 성공시킨 "바로 이 offerId"의 재시도(더블클릭/네트워크 재시도) — 멱등하게 조용히 반환한다.
                return;
            }
            throw new BusinessException(MatchingErrorCode.ALREADY_ACCEPTED_BY_OTHER);
        }

        if (order.getOrderCd() != OrderCd.MATCHING) {
            throw new BusinessException(MatchingErrorCode.NOT_ACCEPTABLE_STATUS);
        }

        order.markPendingBoormiConfirmation(dreamiId, offerId);

        // 엔진은 수락 즉시 부르미에게 확인 팝업을 보내고, 부르미의 확정은 주문이 PENDING_BOORMI_CONFIRMATION 인지
        // 검사하므로 커밋 후에 제출해야 한다. 커밋 후 처리는 MatchingService 의 리스너가 담당한다.
        // ApplicationEventPublisher: Spring이 제공하는 이벤트 발행기. publishEvent로 이벤트 객체를 던지면,
        // 이 이벤트를 구독(@EventListener 등)하는 다른 코드가 (설정에 따라 트랜잭션 커밋 이후) 비동기적으로 실행된다.
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
     * 오퍼(수락 전 콜)의 물품 사진 URL을 조회한다. 아직 수락 전이라 주문의 dreami_id가 비어있어
     * "이 주문의 드리미"로는 검증할 수 없으므로, 이 오퍼를 받은 드리미 본인인지로 검증한다.
     * 매칭 엔진의 SSE 발송 경로(단일 스레드로 직렬화됨)에 S3 조회를 얹지 않기 위해, URL은 이렇게
     * 드리미가 사진 버튼을 눌러 필요할 때만 호출하는 별도 API로 뗀다. 사진이 없거나 조회 실패 시 null.
     */
    @Transactional(readOnly = true)
    public OfferItemPhotoDto getOfferItemPhoto(UUID offerId, UUID dreamiId) {
        if (!matchingService.isDreamiOfferOwner(offerId, dreamiId)) {
            throw new BusinessException(MatchingErrorCode.NOT_OFFER_OWNER);
        }
        UUID orderId = matchingService.findOrderIdByOfferId(offerId)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
        String imageKey = orderService.getOrder(orderId).getImageKey();
        String itemPhotoUrl = imageKey == null ? null : s3PresignService.resolveDownloadUrl(imageKey);
        return new OfferItemPhotoDto(itemPhotoUrl);
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
        // Optional.map(메서드참조): 값이 있으면 OrderSummaryDto.from(그값)으로 변환하고, 없으면 아무것도 안 함.
        // OrderSummaryDto::from 은 order -> OrderSummaryDto.from(order) 람다를 줄여 쓴 정적 메서드 참조.
        // orElse(null): 최종적으로 값이 없으면 null 반환(진행 중 배달이 없는 게 정상 상태라서 예외 대신 null).
        return orderRepository.findByDreamiIdAndOrderCd(dreamiId, OrderCd.IN_PROGRESS)
                .map(OrderSummaryDto::from)
                .orElse(null);
    }

    /**
     * 드리미가 지정한 좌표 반경 내에 열려있는 콜(주문)을 거리순으로 조회한다. 각 콜에 예상 수익/소요시간을 함께 담아 반환한다.
     *
     * <p>matching이 돌려준 건 위치/거리뿐이라 화면에 보여줄 품목/예상수익/ETA는 order 도메인에서 채운다. 주문마다 조회하면 최대 10번의 PK 조회가 되므로 id를 모아 한 번에
     * 읽고, 거리순은 matching이 돌려준 순서로 다시 맞춘다.
     */
    @Transactional(readOnly = true)
    public List<NearbyCallDto> findNearbyCalls(NearbyOrderRequest request) {
        List<NearbyOrderDto> nearbyOrders = nearbyOrderFinder.find(request);
        if (nearbyOrders.isEmpty()) {
            return List.of();
        }

        // 인메모리 매칭 엔진이 orderId로 키를 잡고 있어 중복 id는 나오지 않는다.
        // stream()...toList(): 리스트를 순회하며 각 원소를 orderId만 뽑아 새 리스트로 만드는 파이프라인.
        // Collectors.toMap(키뽑는함수, 값뽑는함수): 리스트를 Map<UUID, NearbyCallOrderDto>로 변환 — 여기선
        // orderId를 키로, order 객체 자체를 값으로 사용해 "id로 빠르게 찾기" 위한 Map을 만든다.
        Map<UUID, NearbyCallOrderDto> ordersById = orderRepository
                .findNearbyCallOrders(nearbyOrders.stream().map(NearbyOrderDto::orderId).toList())
                .stream()
                .collect(Collectors.toMap(NearbyCallOrderDto::orderId, order -> order));

        // 매칭 엔진(인메모리)과 DB가 어긋나 주문이 없는 건은 목록에서 빼고 나머지는 그대로 보여준다.
        // filter(조건 람다): 조건이 true인 원소만 남긴다. map(변환 람다): 남은 원소를 NearbyCallDto로 바꾼다.
        return nearbyOrders.stream()
                .filter(nearby -> ordersById.containsKey(nearby.orderId()))
                .map(nearby -> NearbyCallDto.from(nearby, ordersById.get(nearby.orderId())))
                .toList();
    }

    /**
     * 드리미 대시보드 — 완료 건수, 이번 달 수익, 지난달 대비 증감률, 시장 평균 단가 대비 초과 수익, 최근 6개월 수익 추이를 조회한다. 증감률은 지난달 수익이 0이면 0%로 처리하고, 반올림해 소수점
     * 없이 반환한다.
     */
    @Transactional(readOnly = true)
    public DreamiDashboardDto getDashboard(UUID dreamiId) {
        // 1) 완료 건수는 전체 기간 누적이라 별도로, 상태(COMPLETED)로 단순 COUNT 쿼리 한 번.
        long completedCount = orderRepository.countByDreamiIdAndOrderCd(dreamiId, OrderCd.COMPLETED);

        // 2) "이번 달"과 "5개월 전"(=이번 달 포함 총 6개월 구간의 시작)을 YearMonth로 계산해 둔다.
        //    YearMonth는 java.time 패키지의 "연-월"만 표현하는 값 타입(일자·시간 없음).
        YearMonth thisMonth = YearMonth.now();
        YearMonth rangeStart = thisMonth.minusMonths(5);

        // 3) 최근 6개월치 "정산 완료(SETTLEMENT/SETTLED)" 합계를 월별로 한 번에 조회한다.
        //    - moneyTxRepository.aggregateByBoormiIdAndTypeBetween(...)의 실제 쿼리는 GROUP BY YEAR(...), MONTH(...)로
        //      "그 달의 합계 금액 + 건수"를 월 단위 한 줄씩 묶어서 리턴한다(달마다 따로 6번 쿼리하지 않고 딱 1번).
        //    - WALLET(결제 지갑)은 boormi_id로 소유자를 식별하는데, 드리미 계정의 dreamiId는 boormiId와 동일한 값이라
        //      dreamiId를 그대로 boormiId 자리에 넘겨도 된다.
        //    - .stream().collect(Collectors.toMap(키뽑기, 값뽑기)): 쿼리 결과 List를
        //      Map<YearMonth, MonthlyMoneyAggregate>로 바꾼다. MonthlyMoneyAggregate::yearMonth는
        //      "그 원소의 yearMonth() 메서드 결과를 키로 쓴다"는 뜻의 메서드 참조, aggregate -> aggregate는
        //      "값은 원소 그대로 쓴다"는 람다. 이렇게 Map으로 바꿔두면 아래에서 "그 달 금액이 얼마냐"를
        //      매번 리스트를 뒤지지 않고 byMonth.get(월)로 바로 찾을 수 있다(뒤에서는 amountOf/countOf 헬퍼로 감쌈).
        Map<YearMonth, MonthlyMoneyAggregate> byMonth = moneyTxRepository
                .aggregateByBoormiIdAndTypeBetween(dreamiId, MoneyTxTypeCd.SETTLEMENT, MoneyTxStatusCd.SETTLED,
                        rangeStart.atDay(1).atStartOfDay(), thisMonth.plusMonths(1).atDay(1).atStartOfDay())
                .stream()
                .collect(Collectors.toMap(MonthlyMoneyAggregate::yearMonth, aggregate -> aggregate));

        // 4) 이번 달/지난 달 금액을 Map에서 꺼낸다(그 달 데이터가 없으면 amountOf가 0을 돌려줌 — 아래 헬퍼 참고).
        long thisMonthRevenue = amountOf(byMonth, thisMonth);
        long lastMonthRevenue = amountOf(byMonth, thisMonth.minusMonths(1));
        // 5) 전월 대비 증감률(%). 지난달이 0원이면 나눗셈이 불가능(0으로 나누기)하므로 그 경우만 0%로 처리한다.
        //    (thisMonthRevenue - lastMonthRevenue) * 100.0 에서 100.0(정수가 아니라 double)을 곱해야
        //    정수끼리의 나눗셈(소수점이 버려지는 정수 나눗셈)이 아니라 실수 나눗셈이 되어 소수점이 살아남는다.
        //    Math.round(...)로 반올림해 정수(long)로 맞춘다 — 화면엔 "12%"처럼 소수점 없이 보여주기 때문.
        long growthPercent = lastMonthRevenue == 0 ? 0
                : Math.round((thisMonthRevenue - lastMonthRevenue) * 100.0 / lastMonthRevenue);

        // 6) 이번 달 완료 건수도 같은 Map에서 꺼낸다.
        long thisMonthCount = countOf(byMonth, thisMonth);

        // 7) 최근 6개월(과거→현재순) 수익 추이 리스트를 만든다.
        //    - IntStream.rangeClosed(0, 5): 0,1,2,3,4,5 라는 정수 6개짜리 스트림을 만든다.
        //    - mapToObj(thisMonth::minusMonths): 각 정수 n을 "이번 달에서 n개월 전"(YearMonth 값)으로 바꾼다.
        //      thisMonth::minusMonths 는 n -> thisMonth.minusMonths(n) 람다를 줄여 쓴 메서드 참조.
        //      이 시점에는 0(이번 달), 1(한 달 전), 2(두 달 전)... 순서라 최신 → 과거 순이다.
        //    - sorted(): YearMonth는 Comparable이라 그대로 정렬하면 과거 → 최신(오름차순) 순서로 뒤집힌다.
        //      화면 그래프는 왼쪽이 과거, 오른쪽이 최신이어야 하므로 이 정렬이 필요하다.
        //    - map(month -> new MonthlyRevenueDto(month, amountOf(byMonth, month))): 정렬된 각 달을
        //      "그 달, 그 달의 금액" 쌍으로 감싼 DTO로 바꾼다. new MonthlyRevenueDto(...)는 record의 생성자 호출.
        //    - toList(): 최종적으로 List<MonthlyRevenueDto>로 모은다.
        List<MonthlyRevenueDto> recentSixMonths = IntStream.rangeClosed(0, 5)
                .mapToObj(thisMonth::minusMonths)
                .sorted()
                .map(month -> new MonthlyRevenueDto(month, amountOf(byMonth, month)))
                .toList();

        // 8) 지금까지 계산한 5개 값을 하나의 응답 DTO로 묶어 반환한다.
        return DreamiDashboardDto.of(completedCount, thisMonthRevenue, growthPercent,
                thisMonthCount, recentSixMonths);
    }

    /**
     * 드리미가 수행한(수행 중인) 배달을 최신순으로 커서 페이지네이션 조회한다. {@code statusFilter}가 null이면 전체 탭이다.
     */
    @Transactional(readOnly = true)
    public BoormiOrdersResponse getMyOrders(UUID dreamiId, List<OrderCd> statusFilter, String cursor, Integer size) {
        return orderService.getOrders(dreamiId, Role.DREAMI, statusFilter, cursor, size);
    }

    /**
     * 활동 내역 화면의 상태별(전체/진행중/완료/취소) 탭 개수.
     */
    @Transactional(readOnly = true)
    public List<OrderStatusCountDto> getMyDeliveryStatusCounts(UUID dreamiId) {
        return orderService.getStatusCounts(dreamiId, Role.DREAMI);
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

    // getDashboard 전용 헬퍼. byMonth(월별 집계 Map)에 그 달 데이터가 없으면(거래가 아예 없던 달) null이 나오는데,
    // 호출부마다 null 체크를 반복하지 않도록 여기서 "없으면 0"으로 통일해 돌려준다.
    private long amountOf(Map<YearMonth, MonthlyMoneyAggregate> byMonth, YearMonth month) {
        MonthlyMoneyAggregate aggregate = byMonth.get(month);
        return aggregate == null ? 0 : aggregate.totalAmount();
    }

    // amountOf와 같은 이유로, 그 달의 "건수"만 뽑아서 돌려준다(없으면 0).
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
        LocalDate today = LocalDate.now(); // 날짜만
        LocalDateTime start = today.atStartOfDay(); // 날짜 + 시간
        LocalDateTime end = today.plusDays(1).atStartOfDay();

        // WALLET 은 boormi_id 로 소유자를 식별하며, dreamiId 는 boormiId 와 동일한 값이다.
        // mapToLong(메서드참조): 각 집계 객체에서 금액(long)만 뽑아내 IntStream 계열(LongStream)로 바꾸고,
        // sum()으로 전부 더한다.
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
        // 각 Dreami 엔티티를 람다로 DreamiReviewDto로 변환(이미지 key -> presigned URL로 치환)하며 새 리스트를 만든다.
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

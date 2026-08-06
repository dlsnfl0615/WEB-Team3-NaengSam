package com.naengsam.quick.domain.dreami.service;

import com.naengsam.quick.domain.boormi.entity.Boormi;
import com.naengsam.quick.domain.boormi.repository.BoormiRepository;
import com.naengsam.quick.domain.dreami.dto.DreamiProfileDto;
import com.naengsam.quick.domain.dreami.dto.NearbyCallDto;
import com.naengsam.quick.domain.dreami.entity.Dreami;
import com.naengsam.quick.domain.dreami.entity.DreamiCd;
import com.naengsam.quick.domain.dreami.exception.DreamiErrorCode;
import com.naengsam.quick.domain.dreami.repository.DreamiRepository;
import com.naengsam.quick.domain.dreami.repository.DreamiRequestDeniedDetailsRepository;
import com.naengsam.quick.domain.matching.dto.GeoPoint;
import com.naengsam.quick.domain.matching.dto.NearbyOrderDto;
import com.naengsam.quick.domain.matching.dto.NearbyOrderRequest;
import com.naengsam.quick.domain.matching.exception.MatchingErrorCode;
import com.naengsam.quick.domain.matching.service.MatchingService;
import com.naengsam.quick.domain.matching.service.NearbyOrderFinder;
import com.naengsam.quick.domain.order.dto.OrderSummaryDto;
import com.naengsam.quick.domain.order.entity.OrderCd;
import com.naengsam.quick.domain.order.entity.Orders;
import com.naengsam.quick.domain.order.exception.OrderErrorCode;
import com.naengsam.quick.domain.order.repository.OrderRepository;
import com.naengsam.quick.global.exception.BusinessException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DreamiService {

    private final DreamiRepository dreamiRepository;
    private final BoormiRepository boormiRepository;
    private final DreamiRequestDeniedDetailsRepository dreamiRequestDeniedDetailsRepository;
    private final OrderRepository orderRepository;
    private final NearbyOrderFinder nearbyOrderFinder;
    private final MatchingService matchingService;

    @Transactional
    public void saveVerificationFileKeys(UUID dreamiId, String idCardKey, String criminalRecordKey) {
        dreamiRepository.save(Dreami.create(dreamiId, idCardKey, criminalRecordKey));
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
     * 드리미가 제안을 수락한다. 매칭엔진에 반영하기 전에 주문을 PENDING_BOORMI_CONFIRMATION으로 전이해 DB에도 반영한다
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
        matchingService.acceptByDreami(offerId);
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
     */
    @Transactional(readOnly = true)
    public OrderSummaryDto findCurrentDeliveryCard(UUID dreamiId) {
        Orders order = orderRepository.findByDreamiIdAndOrderCd(dreamiId, OrderCd.IN_PROGRESS)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
        return OrderSummaryDto.from(order);
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
}

package com.naengsam.quick.domain.order.service;

import com.naengsam.quick.domain.boormi.repository.BoormiRepository;
import com.naengsam.quick.domain.dreami.repository.DreamiRepository;
import com.naengsam.quick.domain.order.dto.ReviewDto;
import com.naengsam.quick.domain.order.entity.BoormiReview;
import com.naengsam.quick.domain.order.entity.DreamiReview;
import com.naengsam.quick.domain.order.entity.OrderCd;
import com.naengsam.quick.domain.order.entity.Orders;
import com.naengsam.quick.domain.order.entity.Role;
import com.naengsam.quick.domain.order.exception.OrderErrorCode;
import com.naengsam.quick.domain.order.repository.BoormiReviewRepository;
import com.naengsam.quick.domain.order.repository.DreamiReviewRepository;
import com.naengsam.quick.global.exception.BusinessException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 완료된 배달 건에 대한 상호 리뷰. 부르미와 드리미가 같은 메서드를 호출하고, 로그인 사용자가 주문의 어느 쪽인지에 따라 리뷰가 쌓이는 상대방 테이블만 갈린다. 별점을 먼저 남기고
 * 리뷰 내용은 나중에 채운다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewService {

    private static final int AVG_SCORE_SCALE = 2; // 컬럼 타입이 DECIMAL(3,2)

    private final OrderService orderService;
    private final BoormiReviewRepository boormiReviewRepository;
    private final DreamiReviewRepository dreamiReviewRepository;
    private final BoormiRepository boormiRepository;
    private final DreamiRepository dreamiRepository;

    /**
     * 상대방에게 별점을 남긴다. 완료된 주문에만, 주문당 한 번만 가능하다. 저장 후 상대방의 평균 평점을 다시 계산해 반영한다.
     */
    @Transactional
    public ReviewDto writeScore(UUID orderId, UUID userId, int score) {
        Orders order = orderService.getOrder(orderId);
        if (order.getOrderCd() != OrderCd.COMPLETED) {
            throw new BusinessException(OrderErrorCode.CANNOT_REVIEW);
        }

        if (resolveReviewer(order, userId) == Role.BOORMI) {
            if (dreamiReviewRepository.findByOrderId(orderId).isPresent()) {
                throw new BusinessException(OrderErrorCode.ALREADY_REVIEWED);
            }
            DreamiReview review = dreamiReviewRepository.save(DreamiReview.create(orderId, score));
            updateDreamiAvgScore(order.getDreamiId());
            return ReviewDto.from(review);
        }

        if (boormiReviewRepository.findByOrderId(orderId).isPresent()) {
            throw new BusinessException(OrderErrorCode.ALREADY_REVIEWED);
        }
        BoormiReview review = boormiReviewRepository.save(BoormiReview.create(orderId, score));
        updateBoormiAvgScore(order.getBoormiId());
        return ReviewDto.from(review);
    }

    /**
     * 먼저 남긴 별점 리뷰에 내용을 채우거나 수정한다. 별점을 남기지 않았다면 REVIEW_NOT_FOUND 예외를 던진다.
     */
    @Transactional
    public ReviewDto writeContent(UUID orderId, UUID userId, String content) {
        Orders order = orderService.getOrder(orderId);

        if (resolveReviewer(order, userId) == Role.BOORMI) {
            DreamiReview review = dreamiReviewRepository.findByOrderId(orderId)
                    .orElseThrow(() -> new BusinessException(OrderErrorCode.REVIEW_NOT_FOUND));
            review.updateContent(content);
            return ReviewDto.from(review);
        }

        BoormiReview review = boormiReviewRepository.findByOrderId(orderId)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.REVIEW_NOT_FOUND));
        review.updateDetail(content);
        return ReviewDto.from(review);
    }

    /**
     * 내가 이 주문에 남긴 리뷰를 조회한다. 별점 작성 후 내용을 채우러 다시 들어올 때 쓴다.
     */
    @Transactional(readOnly = true)
    public ReviewDto getMyReview(UUID orderId, UUID userId) {
        Orders order = orderService.getOrder(orderId);

        if (resolveReviewer(order, userId) == Role.BOORMI) {
            return dreamiReviewRepository.findByOrderId(orderId)
                    .map(ReviewDto::from)
                    .orElseThrow(() -> new BusinessException(OrderErrorCode.REVIEW_NOT_FOUND));
        }

        return boormiReviewRepository.findByOrderId(orderId)
                .map(ReviewDto::from)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.REVIEW_NOT_FOUND));
    }

    /**
     * 상대방이 나에게 남긴 리뷰를 조회한다. getMyReview와 반대 방향(내가 작성한 리뷰가 아니라 내가 받은 리뷰)이며,
     * 아직 상대방이 리뷰를 안 남겼으면 예외 대신 null을 반환한다(활동 내역에서 "아직 리뷰가 없어요" 표시용).
     */
    @Transactional(readOnly = true)
    public ReviewDto getReceivedReview(UUID orderId, UUID userId) {
        Orders order = orderService.getOrder(orderId);

        if (resolveReviewer(order, userId) == Role.BOORMI) {
            // 나는 부르미 → 내가 받은 리뷰는 BOORMI_REVIEW(드리미가 작성).
            return boormiReviewRepository.findByOrderId(orderId).map(ReviewDto::from).orElse(null);
        }

        // 나는 드리미 → 내가 받은 리뷰는 DREAMI_REVIEW(부르미가 작성).
        return dreamiReviewRepository.findByOrderId(orderId).map(ReviewDto::from).orElse(null);
    }

    /**
     * 로그인 사용자가 이 주문에서 어느 쪽인지 판별한다. 둘 다 아니면 접근 권한이 없다.
     */
    private Role resolveReviewer(Orders order, UUID userId) {
        if (userId.equals(order.getBoormiId())) {
            return Role.BOORMI;
        }
        if (userId.equals(order.getDreamiId())) {
            return Role.DREAMI;
        }
        throw new BusinessException(OrderErrorCode.NOT_ORDER_OWNER);
    }

    /**
     * 드리미가 받은 전체 리뷰의 평균 별점을 다시 계산해 DREAMI 에 반영한다. 드리미 행이 없으면(인증 신청 전 등) 건너뛴다.
     */
    private void updateDreamiAvgScore(UUID dreamiId) {
        Double avg = dreamiReviewRepository.findAvgScoreByDreamiId(dreamiId);
        if (avg == null) {
            return;
        }
        dreamiRepository.findById(dreamiId)
                .ifPresent(dreami -> dreami.updateAvgScore(toAvgScore(avg)));
    }

    /**
     * 부르미가 받은 전체 리뷰의 평균 별점을 다시 계산해 BOORMI 에 반영한다.
     */
    private void updateBoormiAvgScore(UUID boormiId) {
        Double avg = boormiReviewRepository.findAvgScoreByBoormiId(boormiId);
        if (avg == null) {
            return;
        }
        boormiRepository.findById(boormiId)
                .ifPresent(boormi -> boormi.updateAvgScore(toAvgScore(avg)));
    }

    private BigDecimal toAvgScore(double avg) {
        return BigDecimal.valueOf(avg).setScale(AVG_SCORE_SCALE, RoundingMode.HALF_UP);
    }
}

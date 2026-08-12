package com.naengsam.quick.domain.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.naengsam.quick.domain.boormi.entity.Boormi;
import com.naengsam.quick.domain.boormi.repository.BoormiRepository;
import com.naengsam.quick.domain.dreami.entity.Dreami;
import com.naengsam.quick.domain.dreami.repository.DreamiRepository;
import com.naengsam.quick.domain.matching.dto.GeoPoint;
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
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 완료된 주문에 대한 상호 리뷰 로직 단위 테스트. 작성자 판별(부르미/드리미)과 그에 따라 갈리는 저장 대상, 평균 평점 갱신을 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    private static final UUID BOORMI_ID = UUID.randomUUID();
    private static final UUID DREAMI_ID = UUID.randomUUID();
    private static final UUID ORDER_ID = UUID.randomUUID();

    @Mock
    private OrderService orderService;

    @Mock
    private BoormiReviewRepository boormiReviewRepository;

    @Mock
    private DreamiReviewRepository dreamiReviewRepository;

    @Mock
    private BoormiRepository boormiRepository;

    @Mock
    private DreamiRepository dreamiRepository;

    @InjectMocks
    private ReviewService reviewService;

    /**
     * 매칭이 끝나 드리미까지 배정된 주문. dreamiId·orderCd 는 setter 가 없으므로 리플렉션으로 강제한다.
     */
    private static Orders completedOrder() {
        GeoPoint point = new GeoPoint(new BigDecimal("37.0"), new BigDecimal("127.0"));
        Orders order = Orders.create(ORDER_ID, BOORMI_ID, point, point);
        ReflectionTestUtils.setField(order, "dreamiId", DREAMI_ID);
        ReflectionTestUtils.setField(order, "orderCd", OrderCd.COMPLETED);
        return order;
    }

    private static Boormi boormi() {
        return Boormi.create("user@test.com", "pw", "부르미", "01000000000", LocalDate.of(2000, 1, 1));
    }

    private static Dreami dreami() {
        return Dreami.create(DREAMI_ID, "id-card-key", "criminal-record-key");
    }

    @Test
    void 부르미가_별점을_남기면_DREAMI_REVIEW에_저장되고_드리미_평균평점이_갱신된다() {
        Dreami dreami = dreami();
        given(orderService.getOrder(ORDER_ID)).willReturn(completedOrder());
        given(dreamiReviewRepository.findByOrderId(ORDER_ID)).willReturn(Optional.empty());
        given(dreamiReviewRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));
        given(dreamiReviewRepository.findAvgScoreByDreamiId(DREAMI_ID)).willReturn(4.333);
        given(dreamiRepository.findById(DREAMI_ID)).willReturn(Optional.of(dreami));

        ReviewDto review = reviewService.writeScore(ORDER_ID, BOORMI_ID, 5);

        assertThat(review.reviewerRole()).isEqualTo(Role.BOORMI);
        assertThat(review.score()).isEqualTo(5);
        assertThat(review.content()).isNull();
        assertThat(dreami.getDreamiAvgScore()).isEqualByComparingTo("4.33");

        ArgumentCaptor<DreamiReview> captor = ArgumentCaptor.forClass(DreamiReview.class);
        then(dreamiReviewRepository).should().save(captor.capture());
        assertThat(captor.getValue().getOrderId()).isEqualTo(ORDER_ID);
        then(boormiReviewRepository).should(never()).save(any());
    }

    @Test
    void 드리미가_별점을_남기면_BOORMI_REVIEW에_저장되고_부르미_평균평점이_갱신된다() {
        Boormi boormi = boormi();
        given(orderService.getOrder(ORDER_ID)).willReturn(completedOrder());
        given(boormiReviewRepository.findByOrderId(ORDER_ID)).willReturn(Optional.empty());
        given(boormiReviewRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));
        given(boormiReviewRepository.findAvgScoreByBoormiId(BOORMI_ID)).willReturn(3.0);
        given(boormiRepository.findById(BOORMI_ID)).willReturn(Optional.of(boormi));

        ReviewDto review = reviewService.writeScore(ORDER_ID, DREAMI_ID, 3);

        assertThat(review.reviewerRole()).isEqualTo(Role.DREAMI);
        assertThat(review.score()).isEqualTo(3);
        assertThat(boormi.getBoormiAvgScore()).isEqualByComparingTo("3.00");
        then(dreamiReviewRepository).should(never()).save(any());
    }

    @Test
    void 주문_참여자가_아니면_NOT_ORDER_OWNER_예외() {
        given(orderService.getOrder(ORDER_ID)).willReturn(completedOrder());

        Throwable thrown = catchThrowable(() -> reviewService.writeScore(ORDER_ID, UUID.randomUUID(), 5));

        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(OrderErrorCode.NOT_ORDER_OWNER);
        then(dreamiReviewRepository).should(never()).save(any());
        then(boormiReviewRepository).should(never()).save(any());
    }

    @Test
    void 주문이_COMPLETED가_아니면_CANNOT_REVIEW_예외이고_리뷰를_저장하지_않는다() {
        Orders order = completedOrder();
        ReflectionTestUtils.setField(order, "orderCd", OrderCd.IN_PROGRESS);
        given(orderService.getOrder(ORDER_ID)).willReturn(order);

        Throwable thrown = catchThrowable(() -> reviewService.writeScore(ORDER_ID, BOORMI_ID, 5));

        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(OrderErrorCode.CANNOT_REVIEW);
        then(dreamiReviewRepository).should(never()).save(any());
    }

    @Test
    void 이미_리뷰를_남겼으면_ALREADY_REVIEWED_예외() {
        given(orderService.getOrder(ORDER_ID)).willReturn(completedOrder());
        given(dreamiReviewRepository.findByOrderId(ORDER_ID))
                .willReturn(Optional.of(DreamiReview.create(ORDER_ID, 4)));

        Throwable thrown = catchThrowable(() -> reviewService.writeScore(ORDER_ID, BOORMI_ID, 5));

        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(OrderErrorCode.ALREADY_REVIEWED);
        then(dreamiReviewRepository).should(never()).save(any());
    }

    @Test
    void 별점을_남기지_않고_내용을_보내면_REVIEW_NOT_FOUND_예외() {
        given(orderService.getOrder(ORDER_ID)).willReturn(completedOrder());
        given(dreamiReviewRepository.findByOrderId(ORDER_ID)).willReturn(Optional.empty());

        Throwable thrown = catchThrowable(() -> reviewService.writeContent(ORDER_ID, BOORMI_ID, "친절했어요"));

        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(OrderErrorCode.REVIEW_NOT_FOUND);
    }

    @Test
    void 부르미가_내용을_보내면_기존_별점_리뷰의_content가_채워진다() {
        DreamiReview saved = DreamiReview.create(ORDER_ID, 5);
        given(orderService.getOrder(ORDER_ID)).willReturn(completedOrder());
        given(dreamiReviewRepository.findByOrderId(ORDER_ID)).willReturn(Optional.of(saved));

        ReviewDto review = reviewService.writeContent(ORDER_ID, BOORMI_ID, "친절했어요");

        assertThat(review.reviewId()).isEqualTo(saved.getReviewId());
        assertThat(review.score()).isEqualTo(5);
        assertThat(review.content()).isEqualTo("친절했어요");
        assertThat(saved.getUpdatedDtm()).isNotNull();
    }

    @Test
    void 드리미가_내용을_보내면_기존_별점_리뷰의_detail이_채워진다() {
        BoormiReview saved = BoormiReview.create(ORDER_ID, 2);
        given(orderService.getOrder(ORDER_ID)).willReturn(completedOrder());
        given(boormiReviewRepository.findByOrderId(ORDER_ID)).willReturn(Optional.of(saved));

        ReviewDto review = reviewService.writeContent(ORDER_ID, DREAMI_ID, "연락이 안 됐어요");

        assertThat(saved.getDetail()).isEqualTo("연락이 안 됐어요");
        assertThat(review.content()).isEqualTo("연락이 안 됐어요");
        assertThat(review.reviewerRole()).isEqualTo(Role.DREAMI);
    }

    @Test
    void 내가_남긴_리뷰를_조회하면_별점과_내용을_반환한다() {
        DreamiReview saved = DreamiReview.create(ORDER_ID, 5);
        saved.updateContent("친절했어요");
        given(orderService.getOrder(ORDER_ID)).willReturn(completedOrder());
        given(dreamiReviewRepository.findByOrderId(ORDER_ID)).willReturn(Optional.of(saved));

        ReviewDto review = reviewService.getMyReview(ORDER_ID, BOORMI_ID);

        assertThat(review.score()).isEqualTo(5);
        assertThat(review.content()).isEqualTo("친절했어요");
    }
}

package com.naengsam.quick.domain.matching.model;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * 점수·적격성·scope 정책이 읽는 후보 값의 공통 뷰.
 * <p>구현체는 불변 값 객체이거나 호출 중에만 재사용되는 cursor일 수 있다. 정책 구현체는 이 뷰를 보관하거나 변경해서는
 * 안 되며, 메서드 호출이 끝난 뒤에도 같은 값이 유지된다고 가정해서는 안 된다.
 */
public interface MatchingCandidateView {

    UUID orderId();

    UUID dreamiId();

    double distanceMeters();

    Duration orderWaitingTime();

    Duration dreamiWaitingTime();

    int orderCandidateCount();

    int dreamiCandidateCount();

    Optional<PreviousOfferInteraction> previousInteraction();
}

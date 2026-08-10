package com.naengsam.quick.domain.matching.policy.assignment;

/**
 * 주문·드리미·후보 조합({@link MatchingAssignmentProblem})을 받아 한 라운드에 뿌릴 오퍼 후보
 * 목록({@link MatchingPlan})을 만드는 배정 정책의 계약.
 * <p>구현체는 다음을 지켜야 한다.
 * <ul>
 *     <li>입력(problem)의 상태를 변경하지 않는다.</li>
 *     <li>{@code problem.candidates()}에 존재하는 조합만 결과에 포함한다.</li>
 *     <li>같은 주문은 그 주문의 maxConcurrentOffers까지 여러 드리미를 가질 수 있다.</li>
 *     <li>한 드리미는 한 배치(하나의 MatchingPlan) 안에서 하나의 주문에만 배정된다.</li>
 *     <li>결과는 최종 매칭이 아니라 오퍼 후보 목록이다. 오퍼 생성, 상태 변경, SSE 발송 등 부수효과를 수행하지 않는다.</li>
 *     <li>같은 입력에 대해 항상 같은 결과를 반환한다(결정적이어야 한다).</li>
 * </ul>
 */
public interface MatchingAssignmentPolicy {

    MatchingPlan createPlan(MatchingAssignmentProblem problem);
}

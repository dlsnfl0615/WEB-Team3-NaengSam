package com.naengsam.quick.domain.matching.policy.scoring;

import com.naengsam.quick.domain.matching.model.MatchingCandidateView;

/**
 * 매칭 후보의 우선순위를 점수로 환산하는 정책.
 * <p>점수가 낮을수록 우선순위가 높다(먼저 매칭된다). 여러 요소(거리, 대기시간, 후보 수 등)를 가중합해 계산할 수 있으므로
 * 반환값은 음수를 허용한다.
 */
public interface MatchingScorePolicy {
    long score(MatchingCandidateView candidate);
}

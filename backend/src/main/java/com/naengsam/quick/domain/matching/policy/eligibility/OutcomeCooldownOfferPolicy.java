package com.naengsam.quick.domain.matching.policy.eligibility;

import com.naengsam.quick.domain.matching.model.MatchingCandidate;
import com.naengsam.quick.domain.matching.model.PreviousOfferInteraction;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 이전 오퍼 결과에 따라 서로 다른 cooldown을 적용하는 적격성 정책.
 * <p>드리미 거절, 부르미 거절, 드리미 응답 timeout에는 각각 설정된 cooldown을 적용한다. 타의로 회수된
 * WITHDRAWN과 부르미 응답 timeout인 BOORMI_EXPIRED는 즉시 다시 허용한다. 이전 이력이 없는 후보도 바로 적격이다.
 * occurredAt이 evaluatedAt보다 미래이면 입력이 잘못된 것이므로 예외를 던진다.
 */
public class OutcomeCooldownOfferPolicy implements MatchingEligibilityPolicy {

    private final Duration dreamiRejectionCooldown;
    private final Duration boormiRejectionCooldown;
    private final Duration dreamiExpirationCooldown;

    public OutcomeCooldownOfferPolicy(
            Duration dreamiRejectionCooldown,
            Duration boormiRejectionCooldown,
            Duration dreamiExpirationCooldown
    ) {
        this.dreamiRejectionCooldown = requireValid(dreamiRejectionCooldown, "dreamiRejectionCooldown");
        this.boormiRejectionCooldown = requireValid(boormiRejectionCooldown, "boormiRejectionCooldown");
        this.dreamiExpirationCooldown = requireValid(dreamiExpirationCooldown, "dreamiExpirationCooldown");
    }

    @Override
    public boolean isEligible(
            MatchingCandidate candidate,
            LocalDateTime evaluatedAt
    ) {
        if (candidate.previousInteraction().isEmpty()) {
            return true;
        }

        PreviousOfferInteraction interaction = candidate.previousInteraction().orElseThrow();
        LocalDateTime occurredAt = interaction.occurredAt();

        if (occurredAt.isAfter(evaluatedAt)) {
            throw new IllegalArgumentException(
                    "이력 발생 시각은 평가 시각보다 이후일 수 없습니다."
            );
        }

        Duration cooldown = switch (interaction.outcome()) {
            case DREAMI_REJECTED -> dreamiRejectionCooldown;
            case BOORMI_REJECTED -> boormiRejectionCooldown;
            case DREAMI_EXPIRED -> dreamiExpirationCooldown;
            case BOORMI_EXPIRED, WITHDRAWN -> Duration.ZERO;
        };

        Duration elapsed = Duration.between(occurredAt, evaluatedAt);
        return elapsed.compareTo(cooldown) >= 0;
    }

    private static Duration requireValid(Duration cooldown, String fieldName) {
        if (cooldown == null || cooldown.isNegative()) {
            throw new IllegalArgumentException(
                    fieldName + "은 null이거나 음수일 수 없습니다: " + cooldown
            );
        }
        return cooldown;
    }
}

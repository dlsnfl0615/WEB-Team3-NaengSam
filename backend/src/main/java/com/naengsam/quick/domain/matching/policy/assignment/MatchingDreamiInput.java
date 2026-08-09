package com.naengsam.quick.domain.matching.policy.assignment;

import com.naengsam.quick.domain.matching.dto.GeoPoint;
import java.time.Duration;
import java.util.UUID;

/**
 * 배정 알고리즘에 입력되는 드리미 정보.
 */
public record MatchingDreamiInput(UUID dreamiId, GeoPoint location, Duration waitingTime) {
    public MatchingDreamiInput {
        if (dreamiId == null) {
            throw new IllegalArgumentException("dreamiId는 null일 수 없습니다.");
        }
        if (location == null) {
            throw new IllegalArgumentException("location은 null일 수 없습니다.");
        }
        if (waitingTime == null || waitingTime.isNegative()) {
            throw new IllegalArgumentException("waitingTime은 null이거나 음수일 수 없습니다: " + waitingTime);
        }
    }
}

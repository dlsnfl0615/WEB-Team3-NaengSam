# 매칭 적격성 정책 (MatchingEligibilityPolicy)

레거시 `MatchOffer.shouldExcludeFromRematch()`는 "이 드리미를 이 주문에 다시 후보로 넣어도 되는가"를
판단하는 로직이 배정 로직 안에 섞여 있었다. 이를 별도 정책으로 분리하기 위해
`MatchingEligibilityPolicy.isEligible(candidate, evaluatedAt): boolean` 인터페이스를 추가했다.
구현체는 `evaluatedAt`을 직접 `now()`로 구하지 말고 인자로만 받아야 하며(결정적이어야 함), 입력을
변경해서는 안 된다.

이를 위해 `MatchingCandidate`에 `previousInteraction: Optional<PreviousOfferInteraction>` 필드를
추가했다. `PreviousOfferInteraction(outcome, occurredAt)`은 같은 주문-드리미 조합의 가장 최근 오퍼
이력 하나만 담고, `PreviousOfferOutcome`은 `DREAMI_REJECTED`/`BOORMI_REJECTED`/`DREAMI_EXPIRED`/
`BOORMI_EXPIRED`/`WITHDRAWN` 다섯 가지다.

구현체는 두 단계로 만들었다.

- `LegacyOfferPolicy` — 레거시 제외 규칙을 시각 개념 없이 그대로 재현한다. 드리미 본인의 잘못(거절,
  응답 timeout)인 `DREAMI_REJECTED`/`BOORMI_REJECTED`/`DREAMI_EXPIRED`는 영구 제외하고, 드리미 잘못이
  아닌 `WITHDRAWN`/`BOORMI_EXPIRED`는 즉시 다시 허용한다. `evaluatedAt`은 계약을 맞추기 위해서만
  받는다.
- `OutcomeCooldownOfferPolicy` — 여기에 outcome별 cooldown(냉각 기간) 개념을 더한다. `DREAMI_REJECTED`
  /`BOORMI_REJECTED`/`DREAMI_EXPIRED`는 각각 설정된 `Duration`이 지나야 다시 허용하고,
  `WITHDRAWN`/`BOORMI_EXPIRED`는 여전히 즉시 허용(cooldown = ZERO)한다. 경과 시간은
  `Duration.between(occurredAt, evaluatedAt)`으로 계산하며, `occurredAt`이 `evaluatedAt`보다 미래이면
  입력이 잘못된 것이므로 `IllegalArgumentException`을 던진다. 경계값은 "cooldown과 정확히 같으면
  허용"(`elapsed.compareTo(cooldown) >= 0`)으로 맞췄다.

이후 `MatchingAssignmentProblemFactory`가 원시 후보에 이 정책을 적용해 적격 후보만 남은
`MatchingAssignmentProblem`을 만들고, `MatchingPlanValidator`가 배정 결과의 각 제안에 대해서도 같은
정책으로 다시 한번 검증한다(자세한 내용은 `assignment-problem-factory.md` 참고).

# 오퍼 quota 결정 책임을 assembler로 캡슐화

`MatchingAssignmentProblemAssembler`에서 주문 입력(`MatchingOrderInput`)을 만들 때
`matchingPolicyProperties.maxConcurrentOffers()`를 여러 곳에서 직접 읽지 않도록,
`resolveMaxConcurrentOffers()` private 메서드로 quota 계산 지점을 한 곳에 모았다.

지금은 기존 설정값을 그대로 반환할 뿐이라 동작 변화는 없다. 목적은 다음 커밋에서
주문별로 다른 quota를 계산하는 Dynamic 방식을 넣을 때, `assemble()` 본문을 건드리지
않고 `resolveMaxConcurrentOffers()` 내부만 바꿀 수 있게 하는 것이다.

테스트는 여러 WAITING 주문이 모두 동일한 고정 `maxConcurrentOffers`를 받는지
확인하는 케이스(`모든_주문에_동일한_고정_maxConcurrentOffers가_적용된다`)를 추가했다.

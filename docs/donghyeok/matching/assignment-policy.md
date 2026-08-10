# 매칭 배정 정책 (MatchingAssignmentPolicy)

스코어링 정책만으로는 "누구에게 몇 명을 어떤 순서로 배정할지"를 결정할 수 없어, 배정 알고리즘 자체도
교체 가능한 정책으로 뽑아냈다. 입출력을 다음 불변 타입으로 고정했다.

- `MatchingOrderInput` / `MatchingDreamiInput` — 배정 대상 주문·드리미의 원시 정보. 주문의
  `maxConcurrentOffers`(동시에 뿌릴 최대 제안 수)는 1 이상이어야 한다.
- `MatchingAssignmentProblem(evaluatedAt, orders, dreamis, candidates)` — 한 라운드의 배정 문제 정의.
  컬렉션은 방어적으로 복사해 불변으로 유지하고, orderId/dreamiId 중복과 candidate의 orderId/dreamiId가
  orders/dreamis에 실제로 존재하는지를 생성 시점에 검증한다. `candidates`는 이미 필터링된 허용 후보
  목록이라는 전제이며, 거절·만료 이력을 읽어 후보를 거르는 책임은 배정 정책이 아니라 문제를 만드는 쪽에
  있다(이 책임을 실제로 구현한 게 이후의 `MatchingAssignmentProblemFactory`다).
- `MatchingPlan(proposals)` / `MatchingProposal(orderId, dreamiId)` — 배정 결과. 같은 orderId가 여러
  proposal에 걸쳐 나올 수 있지만(한 주문에 여러 드리미 동시 제안), dreamiId는 한 배치 안에서 유일해야
  한다.
- `MatchingAssignmentPolicy.createPlan(problem): MatchingPlan` — 배정 정책의 계약. 입력을 변경하지
  않고, candidates에 없는 조합을 만들지 않으며, 같은 입력에 항상 같은 결과를 내야 한다(결정적).

구현체는 두 가지를 두었다.

- `LegacyOrderFirstAssignmentPolicy` — orders 입력 순서대로 순회하며 각 주문이 자신의
  maxConcurrentOffers만큼 후보를 뽑아간다. 앞선 주문이 항상 먼저 후보를 골라가므로, scorePolicy를
  바꿔도 주문 간 우선순위 자체는 orders 순서가 결정한다. `OrderWaitScorePolicy`와 함께 쓰면 레거시
  `MatchingService.attemptOfferRound`(이벤트 하나당 주문 하나, FIFO)를 그대로 재현한다.
- `ScoreBasedGreedyAssignmentPolicy` — 모든 주문-드리미 후보 쌍을 점수 하나의 전역 순서로 정렬한 뒤
  앞에서부터 그리디하게 배정한다. orders 입력 순서에 의존하지 않으므로, 거리·희소성 등으로 scorePolicy를
  바꾸면 그 효과가 주문 간 우선순위에도 실제로 반영된다. legacy 재현이 필요하면 이 클래스가 아니라
  `LegacyOrderFirstAssignmentPolicy`를 써야 한다.

두 정책 모두 배정 제약을 스스로 검증하지 않으므로, `MatchingPlanValidator`가 주문별
maxConcurrentOffers 초과 여부(및 이후 추가된 적격성 검증)를 별도로 검증한다.

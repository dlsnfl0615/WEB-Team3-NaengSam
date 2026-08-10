# UploadSession 소비(consume) 동시성 처리와 clearAutomatically

## 배경

`UploadSessionService.consume()`은 presigned URL로 발급한 업로드 세션을 "한 번만" 소비 처리해야 한다.
원래는 조회 후 dirty checking으로 flush하는 일반적인 방식(read-then-write)이었는데, 같은 key로 동시에
두 요청이 들어오면 둘 다 `ISSUED`를 읽고 각자 `CONSUMED`로 커밋해버려 중복 소비가 가능했다
(`56809af BE/Fix : UploadSession consume 동시성 문제 수정`).

## 왜 직접 쿼리(markConsumedIfIssued)를 썼는가

일반적인 "조회 → 필드 확인 → 값 바꾸고 save" 방식은 확인(check)과 반영(set) 사이에 틈이 생겨서,
그 틈에 다른 트랜잭션이 끼어들면 이중 소비를 막을 수 없다. 이걸 원자적으로 만들기 위해 조건부
UPDATE 하나로 처리하는 리포지토리 메서드를 도입했다.

```java
@Modifying
@Query("""
        UPDATE UploadSession s
        SET s.status = ..CONSUMED, s.consumedDtm = CURRENT_TIMESTAMP
        WHERE s.s3Key = :s3Key
          AND s.status = ..ISSUED
        """)
int markConsumedIfIssued(@Param("s3Key") String s3Key);
```

`WHERE ... AND status = ISSUED`까지 포함한 UPDATE 문 하나가 DB에서 원자적으로 실행되므로, 같은 key로
동시에 호출돼도 **딱 하나의 호출만** row 1개가 바뀌었다는 결과(`1`)를 받고 나머지는 `0`을 받는다.
반환값 자체가 "이번 호출이 실제로 소비시켰는지"를 알려주는 신호가 되어, `@Version`(낙관적 락) 이나
`SELECT ... FOR UPDATE`(비관적 락) 같은 별도 락 장치 없이도 동시성 안전성을 얻을 수 있다.

```java
@Transactional
public boolean consume(String key) {
    if (uploadSessionRepository.markConsumedIfIssued(key) == 1) {
        return true;
    }
    findByKey(key); // 세션 자체가 없으면 FILE_NOT_FOUND, 있으면(이미 CONSUMED) 그냥 통과
    return false;
}
```

## clearAutomatically=true를 붙인 이유 (그리고 제거한 이유)

`markConsumedIfIssued`는 `@Modifying` 벌크 업데이트라 영속성 컨텍스트를 우회해서 DB에 직접 UPDATE를
날린다. 도입 당시, 같은 트랜잭션에서 `UploadSession`을 이미 로드해둔 상태가 있다면 그게 DB와 안 맞는
캐시로 남을 것을 우려해 안전장치로 `@Modifying(clearAutomatically = true)`를 같이 붙였다.

5일 뒤, 이 설정이 실제 버그를 냈다. `entityManager.clear()`는 "UploadSession과 관련된 것만" 지우는
게 아니라 **그 트랜잭션에 있는 관리 엔티티를 전부** detach시킨다. 실제 서비스 흐름
(`DeliveryService.doPickupFinishByDreami`)은 `Delivery`를 먼저 로드해서 들고 있다가 `checkUpload`
(→ `markConsumedIfIssued`)를 호출하는데, 이때 `clearAutomatically`가 이 **완전히 무관한 `Delivery`
엔티티까지 같이 detach**시켜버렸다. detach된 엔티티에 그 뒤 가한 변경(`markDelivering()`)은 dirty
checking 대상이 아니므로 flush해도 예외 없이 조용히 무시되어, "픽업 완료 처리가 반영되지 않는" 실제
버그로 이어졌다 (`7b64e80 BE/Fix : clearAutomatically=true가 다른 도메인으로 전파되어 영속성 컨텍스트를
모두 없애버리는 문제 수정`).

### 제거해도 안전한 이유

`markConsumedIfIssued`는 벌크 업데이트라 DB에 직접 반영되고, 그 이전에 `validateScope`가 이미
`findByKey`로 같은 `UploadSession`을 한 번 로드해 영속성 컨텍스트(identity map)에 올려둔 상태다.
`clearAutomatically`를 떼면 이 캐시된 엔티티는 벌크 업데이트 이후에도 여전히 "관리 상태"로 남고,
필드 값(`status`)도 실제 DB(CONSUMED)와 다르게 옛 값(ISSUED)을 그대로 들고 있다 — 즉 캐시 자체는
분명히 stale해진다.

`clearAutomatically`가 막으려던 게 바로 이 stale 상태인데, 그럼에도 안전한 이유는 **이 stale한
캐시를 실제로 읽어서 판단에 쓰는 코드가 어디에도 없기 때문**이다.

- "이번 호출로 소비에 성공했는가"는 `markConsumedIfIssued`가 돌려주는 `int`(영향받은 row 수)로만
  판단한다. 이건 SQL이 직접 알려주는 사실이라 영속성 컨텍스트의 캐시 상태와 완전히 무관하다.
- `markConsumedIfIssued`가 `0`을 반환했을 때 호출하는 `findByKey(key)`는 결과를 다시 조회하긴
  하지만, 그 결과에서 쓰는 건 **존재 여부뿐**이다(없으면 `FILE_NOT_FOUND`, 있으면 그냥 통과하고
  `false` 반환). 반환된 엔티티가 identity map에서 재사용된 stale 객체이든 아니든, `status` 필드를
  들여다보는 코드가 없으니 결과가 달라질 일이 없다.

정리하면, `clearAutomatically`가 방어하려던 "stale 캐시를 읽어서 잘못된 판단을 내리는" 시나리오는
캐시가 stale해지는 것 자체는 사실이지만, 그 캐시를 읽는 코드가 없어서 **관찰 가능한 버그로 이어지지
않는다.** 반대로 부작용(다른 도메인 엔티티까지 detach)은 실제로 관찰 가능한 버그였다. 방어하려던
문제는 발생해도 무해하고, 부작용만 유해했으므로 제거하는 것이 맞는 선택이었다.

이 시나리오는 `UploadSessionRepositoryIntegrationTest`(`세션소비_쿼리_이후에도_같은_트랜잭션에서_먼저_로드한_Delivery의_변경이_반영된다`)로 재현·검증한다.

## 참고 커밋

- `56809af` BE/Fix : UploadSession consume 동시성 문제 수정 (도입)
- `7b64e80` BE/Fix : clearAutomatically=true가 다른 도메인으로 전파되어 영속성 컨텍스트를 모두 없애버리는 문제 수정 (제거)

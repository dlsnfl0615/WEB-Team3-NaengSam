# JPA 핵심 개념 면접노트

JPA의 정체(표준 명세 vs 구현체)부터 영속성 컨텍스트, 엔티티 생명주기, 연관관계 매핑, 영속성 전이까지
면접에서 자주 나오는 순서대로 정리.

## 목차

1. [JPA는 정확히 무엇인가](#1-jpa는-정확히-무엇인가)
2. [영속성 컨텍스트와 EntityManager](#2-영속성-컨텍스트와-entitymanager)
3. [엔티티의 생명주기](#3-엔티티의-생명주기)
4. [영속성 컨텍스트가 주는 4가지 핵심 이점](#4-영속성-컨텍스트가-주는-4가지-핵심-이점)
5. [연관관계 매핑](#5-연관관계-매핑)
6. [영속성 전이(Cascade)와 고아 객체 제거(orphanRemoval)](#6-영속성-전이cascade와-고아-객체-제거orphanremoval)

---

## 1. JPA는 정확히 무엇인가

**Q1. JPA와 Hibernate, Spring Data JPA의 관계를 설명해보세요.**

JPA(Java Persistence API)는 **자바 진영의 ORM 표준 명세(specification)**다. 인터페이스와 애노테이션의
집합일 뿐, 그 자체로는 동작하는 코드가 없다. 실제로 SQL을 만들고 DB와 통신하는 건 구현체가 한다.

```
JPA (표준 명세, 인터페이스)
    ↓ 구현
Hibernate (JPA 구현체 중 하나, 실제 동작하는 ORM 프레임워크)
    ↓ 한 단계 더 추상화
Spring Data JPA (Hibernate 등 JPA 구현체를 감싸서, 인터페이스 선언만으로 CRUD/쿼리 자동 생성)
```

즉 "JPA는 표준 인터페이스이고 Hibernate는 그 구현체다"가 정답이다. 실무에서 쓰는 `JpaRepository`는
사실 Spring Data JPA가 제공하는 것이고, 그 뒤에서 Hibernate가 실제 SQL을 생성해서 실행하는 구조다.

ORM(Object-Relational Mapping) 자체의 정의도 짚고 가면: 객체지향 언어의 객체와 관계형 DB의 테이블을
매핑해서, 개발자가 SQL을 직접 작성하지 않고 객체를 다루는 것만으로 DB 작업을 처리할 수 있게 해주는
기술이다. `@Entity`를 붙인 클래스가 테이블에, 필드가 컬럼에 매핑되는 게 그 실체다.

---

## 2. 영속성 컨텍스트와 EntityManager

JPA를 이해하는 데 가장 핵심이자, 면접에서 가장 깊이 파고드는 부분이다.

`EntityManager`는 엔티티를 관리하는 객체로, 엔티티의 저장/조회/수정/삭제를 담당한다. 그리고
`EntityManager`가 내부적으로 가지고 있는, 엔티티를 저장하는 논리적인 공간이 바로 **영속성 컨텍스트**다.

중요한 점은, 영속성 컨텍스트는 물리적인 캐시 저장소가 아니라 개념적인 것이고, `EntityManager`를 통해
엔티티를 저장하거나 조회하면 그 엔티티가 영속성 컨텍스트에 들어간다는 것이다.

Spring Data JPA를 쓰면 이 `EntityManager`를 직접 다룰 일은 거의 없지만, `JpaRepository`의 `save()`,
`findById()` 같은 메서드들이 내부적으로 `EntityManager`를 호출하고 있다는 걸 알아야 동작 원리를
설명할 수 있다.

---

## 3. 엔티티의 생명주기

**Q2. 엔티티가 준영속 상태가 되는 경우는 언제이고, 그때 어떤 문제가 생길 수 있나요?**

면접에서 자주 나오는 4단계 그림이다.

| 상태 | 설명 | 예시 |
|---|---|---|
| 비영속 (new/transient) | 영속성 컨텍스트와 전혀 관계없는 순수한 객체 상태 | `Member member = new Member();` |
| 영속 (managed) | 영속성 컨텍스트에 저장되어 관리되는 상태 | `em.persist(member)` 또는 `repository.save(member)` 후, 혹은 `findById`로 조회된 직후 |
| 준영속 (detached) | 영속성 컨텍스트에 저장되었다가 분리된 상태 | `em.detach(member)`, 트랜잭션(영속성 컨텍스트)이 종료된 후의 엔티티 |
| 삭제 (removed) | 삭제된 상태 | `em.remove(member)` |

**영속 상태일 때만** 변경 감지(dirty checking) 같은 JPA의 강력한 기능들이 동작한다. 준영속 상태가
되면 그 순간부터 영속성 컨텍스트가 더 이상 관리하지 않기 때문에, 값을 바꿔도 DB에 반영되지 않는다.
이건 실무에서 "왜 내가 수정했는데 DB에 반영이 안 되죠?" 같은 버그의 단골 원인이라 면접에서도 자주
물어본다.

---

## 4. 영속성 컨텍스트가 주는 4가지 핵심 이점

**Q3. 영속성 컨텍스트란 무엇이고, 왜 사용하나요?**

"JPA를 쓰면 왜 좋은가"에 대한 답이자, 실제로 가장 많이 물어보는 개념이다.

**① 1차 캐시**

영속성 컨텍스트 내부에는 키(식별자) - 엔티티 형태의 맵이 있다. `findById()`로 조회하면 먼저 이 1차
캐시를 확인하고, 있으면 SQL을 날리지 않고 바로 반환한다. 단, 이 캐시는 같은 트랜잭션(영속성
컨텍스트) 범위 안에서만 유효하다. 트랜잭션이 다르면 공유되지 않으므로, 흔히 오해하는 "여러 요청
간에 캐싱되는 것"과는 다르다.

**② 동일성 보장 (Identity Map)**

같은 트랜잭션 안에서 같은 식별자로 두 번 조회하면, 1차 캐시 덕분에 **완전히 같은 객체(`==` 비교로
`true`)**가 반환된다. 이걸 "1차 트랜잭션 수준의 반복 가능한 읽기(REPEATABLE READ) 등급의 격리 수준을
애플리케이션 차원에서 제공한다"고 설명하기도 한다.

**③ 트랜잭션을 지원하는 쓰기 지연 (Transactional write-behind)**

`persist()`를 호출해도 즉시 INSERT SQL이 날아가지 않는다. 내부의 쓰기 지연 SQL 저장소에 SQL을
쌓아두다가, 커밋되는 시점에 한꺼번에 flush되어 DB로 전송된다. 이 덕분에 JDBC가 지원하는 SQL 배치
기능을 활용할 여지도 생긴다.

**④ 변경 감지 (Dirty Checking)**

**Q4. 변경 감지(Dirty Checking)는 어떻게 동작하나요?**

영속 상태인 엔티티의 필드 값을 바꾸기만 하면, 별도로 `update()` 같은 메서드를 호출하지 않아도
트랜잭션 커밋 시점에 자동으로 UPDATE SQL이 실행된다. 원리는 이렇다.

1. 트랜잭션 커밋 시점에 `flush()` 호출
2. 영속성 컨텍스트는 엔티티를 처음 영속 상태로 만들 때의 스냅샷을 보관하고 있음
3. flush 시점에 스냅샷과 현재 엔티티를 비교
4. 변경된 필드가 있으면 UPDATE SQL을 쓰기 지연 저장소에 등록 후 DB로 전송

**Q5. flush와 commit의 차이는 무엇인가요?**

flush의 정의를 명확히 해야 한다. flush는 "영속성 컨텍스트를 비우는 것"이 아니라, **영속성 컨텍스트의
변경 내용을 DB에 동기화하는 것**이다. flush가 발생해도 1차 캐시나 영속 상태는 그대로 유지된다.
commit은 flush를 포함해서 실제 DB 트랜잭션을 물리적으로 종료(확정)시키는 것이고, flush는 그 과정
중 "SQL을 DB로 내보내는" 단계에 해당한다.

flush는 커밋 시점 외에도 `em.flush()` 직접 호출, JPQL 쿼리 실행 직전에도 자동으로 일어난다(JPQL은
SQL로 직접 변환되기 때문에, flush 안 하면 아직 DB에 반영 안 된 변경사항이 조회에서 누락될 수 있어서다).

---

## 5. 연관관계 매핑

실무 코드에서 매일 마주치고, 면접에서도 거의 빠지지 않는 부분이다. 핵심은 "객체의 참조와 테이블의
외래키는 서로 다르게 동작한다"는 걸 이해하는 것이다.

**Q6. 객체 연관관계와 테이블 연관관계의 차이는 무엇인가요?**

테이블은 외래키(FK) 하나로 두 테이블을 연결하는데, 이 외래키 하나만으로 양쪽 방향 모두 조인이
가능하다. `Order` 테이블에 `member_id`라는 외래키가 있으면, `Order`에서 `Member`로도, `Member`에서
`Order`로도 SQL 조인이 가능하다는 뜻이다. 즉 테이블 연관관계에는 애초에 "방향"이라는 개념이 없다.

반면 객체는 참조를 가진 쪽에서만 다른 쪽으로 접근할 수 있다. `Order`가 `Member` 필드를 가지고 있으면
`order.getMember()`는 가능하지만, `Member`가 `Order`에 대한 참조가 없으면 `member.getOrder()`는
애초에 불가능하다. 양쪽 다 서로를 참조하게 만들면 "양방향"이 되는데, 정확히는 이것도 사실은 단방향
참조 두 개가 겹쳐 있는 것이다. 객체 입장에서 진짜 양방향이라는 건 존재하지 않는다.

이 차이 때문에 "객체를 테이블에 맞추어 매핑할 때, 외래키는 하나인데 참조는 두 개일 수 있다"는 문제가
생기고, 여기서 연관관계의 주인이라는 개념이 나온다.

### 다중성(Multiplicity)

| 애노테이션 | 의미 | 예시 |
|---|---|---|
| `@ManyToOne` | N:1 (외래키를 가진 쪽, 대부분 여기가 연관관계의 주인) | 여러 Order가 한 Member에 속함 |
| `@OneToMany` | 1:N | 한 Member가 여러 Order를 가짐 |
| `@OneToOne` | 1:1 | 한 Member가 하나의 배송지 정보를 가짐 |
| `@ManyToMany` | N:M | (실무에서는 거의 안 씀 — 아래 설명) |

외래키는 항상 "N" 쪽 테이블에 있다. `Order` 테이블에 `member_id`가 있는 것이지, `Member` 테이블에
`order_id`가 여러 개 있을 수는 없기 때문이다. 그래서 `@ManyToOne`이 있는 쪽, 즉 외래키를 가진 쪽이
자연스럽게 연관관계의 주인이 된다.

**Q7. 연관관계의 주인이란 무엇이고, 왜 필요한가요?**

양방향 매핑을 할 때, JPA는 둘 중 어느 쪽 참조를 기준으로 외래키 값을 관리(등록/수정/삭제)할지
정해야 한다. 이때 실제 DB 외래키를 관리하는 쪽을 **연관관계의 주인**이라고 부른다.

```java
@Entity
public class Order {
    @Id @GeneratedValue
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")   // 실제 외래키가 있는 테이블 컬럼 → 여기가 주인
    private Member member;
}

@Entity
public class Member {
    @Id @GeneratedValue
    private Long id;

    @OneToMany(mappedBy = "member")   // "나는 주인이 아니고, Order.member 필드가 주인이다"
    private List<Order> orders = new ArrayList<>();
}
```

규칙은 명확하다.

- 주인은 `@JoinColumn`으로 외래키를 매핑한다. 이 쪽에서 값을 등록/수정하면 실제 외래키
  UPDATE/INSERT가 발생한다.
- 주인이 아닌 쪽은 `mappedBy` 속성으로 "나는 조회만 가능하고, 실제 외래키 관리는 상대방이 한다"는 걸
  명시한다. `mappedBy`의 값은 상대 엔티티에서 자신을 참조하는 필드명이다.
- 주인이 아닌 쪽에 값을 아무리 세팅해도 외래키 값에는 영향이 없다. 이게 실무에서 자주 하는 실수다.

**Q8. mappedBy가 있는 쪽에만 값을 세팅하면 어떤 문제가 생기나요?**

```java
Member member = new Member();
Order order = new Order();

member.getOrders().add(order);  // 이렇게만 하면 DB에 반영 안 됨! (주인이 아닌 쪽만 세팅)

order.setMember(member);        // 이게 있어야 실제 member_id 컬럼에 값이 들어감 (주인 쪽 세팅)
```

연관관계의 주인이 아닌 쪽에만 값을 넣고 끝내면, 객체 그래프상으로는 연결된 것처럼 보여도 실제 DB에는
반영되지 않는다. 이걸 방지하려고 실무에서는 보통 주인 쪽에 연관관계 편의 메서드를 만들어서 양쪽을
한 번에 세팅한다.

```java
public class Order {
    private Member member;

    public void setMember(Member member) {
        this.member = member;
        member.getOrders().add(this); // 양쪽 다 세팅해서 동기화
    }
}
```

**Q9. 외래키를 가진 쪽을 주인으로 정하는 이유는?**

이론상 `mappedBy`를 어느 쪽에 붙일지는 설계자 마음이지만, 외래키가 없는 쪽을 주인으로 정하면 이상한
일이 벌어진다. 예를 들어 `Member`를 주인으로 정하면, `Order`를 저장할 때가 아니라 `Member`를 수정할
때 `Order` 테이블에 UPDATE 쿼리가 나가는 상황이 생긴다. 엔티티 관점에서는 이해하기 힘든 동작이라,
"외래키가 실제로 있는 테이블에 매핑된 엔티티를 주인으로 한다"는 규칙을 따르는 것이 표준이다. 정리하면
**연관관계의 주인 = 외래키를 관리하는 쪽 = 보통 `@ManyToOne` 쪽**이라고 기억하면 된다.

**Q10. @ManyToMany를 실무에서 잘 쓰지 않는 이유는 무엇이고, 대안은 무엇인가요?**

N:M 관계는 관계형 DB에서 직접 표현할 수 없고, 항상 중간 테이블이 필요하다. `@ManyToMany`는 이 중간
테이블을 JPA가 자동으로 숨겨서 관리해주는데, 문제는 중간 테이블에 컬럼을 추가할 수 없다는 것이다.
예를 들어 "언제 좋아요를 눌렀는지", "수량이 몇 개인지" 같은 부가 정보를 저장할 곳이 없다. 그래서
실무에서는 중간 테이블을 엔티티로 직접 승격시켜서, N:M을 두 개의 1:N + N:1 관계로 풀어낸다.

```java
@Entity
public class OrderItem {
    @ManyToOne
    @JoinColumn(name = "order_id")
    private Order order;

    @ManyToOne
    @JoinColumn(name = "item_id")
    private Item item;

    private int count; // 중간 테이블만의 부가 정보
}
```

---

## 6. 영속성 전이(Cascade)와 고아 객체 제거(orphanRemoval)

연관관계 매핑 다음으로 자연스럽게 이어지는 주제다. 지금까지는 "연관관계를 어떻게 맺을지"를
다뤘다면, 이번엔 "부모 엔티티의 상태 변화를 자식 엔티티에게 어디까지 전파할지"에 대한 내용이다.

**Q11. Cascade가 무엇이고, 왜 필요한가요?**

기본적으로 JPA에서 `persist()`나 `remove()`는 해당 엔티티 하나에만 적용된다. 부모를 저장한다고
자식이 자동으로 저장되지 않는다.

```java
Parent parent = new Parent();
Child child1 = new Child();
Child child2 = new Child();

parent.addChild(child1);
parent.addChild(child2);

em.persist(parent); // 이것만으로는 child1, child2가 저장되지 않음!
em.persist(child1);
em.persist(child2); // 원래는 이렇게 자식도 각각 persist 해줘야 함
```

이렇게 부모-자식을 함께 저장할 일이 잦은 경우, `cascade` 옵션을 주면 부모에 대한 영속성 상태 변화를
자식에게도 전이시킬 수 있다.

```java
@Entity
public class Parent {
    @OneToMany(mappedBy = "parent", cascade = CascadeType.PERSIST)
    private List<Child> children = new ArrayList<>();
}
```

```java
em.persist(parent); // cascade 덕분에 child1, child2도 함께 INSERT
```

### Cascade 종류

| 타입 | 의미 |
|---|---|
| PERSIST | 부모를 저장(persist)할 때 자식도 함께 저장 |
| MERGE | 부모를 병합(merge)할 때 자식도 함께 병합 |
| REMOVE | 부모를 삭제할 때 자식도 함께 삭제 |
| REFRESH | 부모를 새로고침할 때 자식도 함께 새로고침 |
| DETACH | 부모를 준영속 상태로 만들 때 자식도 함께 준영속화 |
| ALL | 위 다섯 가지를 모두 적용 |

**Q12. Cascade와 연관관계의 주인(mappedBy)은 어떤 관계인가요? 서로 독립적인 이유는?**

여기서 중요한 오해 하나를 짚어야 한다. cascade는 "연관관계를 맺는 것"과는 아무 상관이 없다. 앞서
다룬 연관관계의 주인이나 `mappedBy`는 "외래키를 누가 관리하느냐"의 문제이고, cascade는 그것과 별개로
"엔티티의 영속성 생명주기 전파 범위"만 결정한다. 그래서 연관관계의 주인이 아닌 쪽(`mappedBy`가 붙은
쪽)에도 cascade를 설정하는 게 자연스럽다 — 보통 부모 쪽에 붙이니까.

**Q13. orphanRemoval은 어떻게 동작하나요?**

부모 엔티티의 컬렉션에서 자식을 제거했을 때(참조를 끊었을 때), 그 자식을 DB에서도 자동으로 삭제해
주는 옵션이다.

```java
@Entity
public class Parent {
    @OneToMany(mappedBy = "parent", orphanRemoval = true)
    private List<Child> children = new ArrayList<>();
}
```

```java
parent.getChildren().remove(child1); // 컬렉션에서만 제거했을 뿐인데
// → 트랜잭션 커밋 시점에 child1에 대한 DELETE 쿼리가 자동으로 나감
```

orphanRemoval은 "더 이상 어떤 부모도 참조하지 않는 자식(고아)은 의미가 없으니 삭제한다"는 개념으로,
cascade와는 별개의 독립된 옵션이다.

**Q14. CascadeType.ALL + orphanRemoval = true를 함께 쓰면 어떤 설계 패턴이 되나요?**

이 둘을 함께 쓰면, 자식 엔티티의 생명주기 전체를 부모를 통해서만 관리할 수 있게 된다. 자식을 직접
`save()`하거나 `delete()`할 필요 없이, 부모의 컬렉션에 추가/제거하는 것만으로 저장/삭제가 자동으로
이루어진다. 이건 **DDD(도메인 주도 설계)의 애그리거트(Aggregate) 개념**과 자연스럽게 연결된다 —
부모가 애그리거트 루트가 되고, 자식은 그 루트를 통해서만 접근/조작되는 구조다.

```java
public class Parent {
    private List<Child> children = new ArrayList<>();

    public void addChild(String name) {
        Child child = new Child(name);
        child.setParent(this);
        children.add(child); // 이것만으로 저장까지 처리됨 (cascade PERSIST)
    }

    public void removeChild(Child child) {
        children.remove(child); // 이것만으로 삭제까지 처리됨 (orphanRemoval)
    }
}
```

**Q15. Cascade를 함부로 쓰면 안 되는 경우는 언제인가요?**

cascade와 orphanRemoval은 강력한 만큼 제약도 명확하다. **자식 엔티티를 소유하는 부모가 단 하나일
때만** 사용해야 한다. 예를 들어 게시글(Post)과 첨부파일(Attachment)처럼, 첨부파일이 오직 하나의
게시글에만 종속되는 관계라면 적합하다. 반대로 하나의 자식을 여러 부모가 공유할 수 있는 구조(예: 여러
주문이 하나의 상품을 참조하는 경우)에서 함부로 `cascade = ALL`이나 `orphanRemoval`을 쓰면, 다른
부모가 참조 중인 엔티티가 예상치 못하게 삭제되는 심각한 버그로 이어질 수 있다. 즉 "이 자식은 이
부모의 생명주기에 완전히 종속되는가?"를 먼저 판단하고 적용해야 한다.

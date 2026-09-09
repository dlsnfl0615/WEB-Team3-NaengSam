# 스프링 트랜잭션 면접노트

`@Transactional`의 동작 원리부터 전파·격리수준·JPA 영속성 컨텍스트·MSA 분산 트랜잭션까지, 자주 나오는
37개 질문과 모범답안.

## 목차

1. [트랜잭션 기본 개념](#1-트랜잭션-기본-개념)
2. [@Transactional 동작 원리](#2-transactional-동작-원리)
3. [전파(Propagation)](#3-전파propagation)
4. [격리 수준(Isolation Level)](#4-격리-수준isolation-level)
5. [롤백 규칙과 예외 처리](#5-롤백-규칙과-예외-처리)
6. [JPA 영속성 컨텍스트](#6-jpa-영속성-컨텍스트)
7. [트랜잭션 매니저](#7-트랜잭션-매니저)
8. [락(Lock)과 동시성](#8-lock과-동시성)
9. [분산 트랜잭션 · MSA](#9-분산-트랜잭션--msa)
10. [테스트와 트러블슈팅](#10-테스트와-트러블슈팅)

---

## 1. 트랜잭션 기본 개념

**Q1. 트랜잭션이란 무엇이며, ACID 각각이 스프링 트랜잭션에서 어떻게 보장되는지 설명해 주세요.**

트랜잭션은 더 이상 나눌 수 없는 하나의 작업 단위다. **원자성(Atomicity)**은 트랜잭션 내 모든 연산이 전부
성공하거나 전부 실패하도록 트랜잭션 매니저와 DB의 undo log가 보장한다. **일관성(Consistency)**은
트랜잭션 전후로 DB가 제약조건(무결성 제약, 외래키 등)을 항상 만족하는 것으로, 애플리케이션 로직과 DB
제약이 함께 책임진다. **격리성(Isolation)**은 동시에 실행되는 트랜잭션들이 서로 영향을 주지 않도록
격리 수준으로 제어된다. **지속성(Durability)**은 커밋된 데이터가 장애 후에도 영구 보존되는 것으로 redo
log(WAL) 등이 보장한다. 스프링은 이 중 원자성·격리성의 제어(전파, 격리수준 지정)를 선언적으로 다루도록
추상화를 제공하고, 지속성·일관성은 주로 DB 엔진의 책임이다.

**Q2. 트랜잭션의 시작과 종료(커밋/롤백) 시점은 정확히 언제인가요?**

`@Transactional`이 붙은 메서드에 프록시를 통해 진입하는 순간, 트랜잭션 매니저가 커넥션을 가져와
`setAutoCommit(false)`로 트랜잭션을 시작한다. 메서드가 정상 종료되면 커밋하고, 런타임 예외(또는 설정된
예외)가 발생하면 롤백 후 프록시 밖으로 예외를 전파한다. 실제 DB 트랜잭션은 커넥션 단위로 관리되므로,
같은 스레드에서 `TransactionSynchronizationManager`가 커넥션을 바인딩해 하위 호출들이 같은 커넥션을
공유하도록 한다.

**Q3. 스프링에서 트랜잭션을 관리하는 방법에는 어떤 것들이 있나요? (선언적 vs 프로그래밍 방식)**

**선언적 방식**은 `@Transactional` 애노테이션과 AOP 프록시로 비즈니스 로직과 트랜잭션 관리 코드를
분리하는 방식이다. **프로그래밍 방식**은 `TransactionTemplate`이나 `PlatformTransactionManager`를 직접
주입받아 코드로 트랜잭션 경계를 명시적으로 제어한다. 실무에서는 선언적 방식을 기본으로 쓰고, 트랜잭션
경계를 세밀하게 나누거나 조건부로 커밋/롤백을 제어해야 할 때 프로그래밍 방식을 보조적으로 사용한다.

### 보충: ACID 각각의 실제 보장 주체

`@Transactional`은 ACID를 직접 구현하는 게 아니라, 트랜잭션 경계를 선언적으로 관리하면서 실제 ACID
보장은 하부 리소스(RDBMS, JTA 트랜잭션 매니저)에 위임하는 구조다. `@Transactional`이 붙은 빈은 AOP
프록시로 감싸지고, 메서드 호출 시 `TransactionInterceptor`가 가로채서 `PlatformTransactionManager`
(`DataSourceTransactionManager`, `JpaTransactionManager`, `JtaTransactionManager` 등)에게 트랜잭션
시작을 요청하고, 메서드가 정상 종료되면 커밋을, 예외가 발생하면 롤백을 호출한다. 이때 트랜잭션
매니저는 실제 `Connection`(또는 `EntityManager`)을 `TransactionSynchronizationManager`를 통해
스레드에 바인딩해두는데, 이 덕분에 같은 트랜잭션 안에서 실행되는 모든 리포지토리/DAO 호출이 물리적으로
동일한 커넥션을 공유하게 된다.

각 속성을 실제로 누가 보장하는지 나눠보면:

- **Atomicity**: "하나의 커넥션 공유 + 메서드 경계에서 commit/rollback 일괄 처리"가 핵심이다. 기본적으로
  `RuntimeException`/`Error`가 발생하면 자동 롤백되고, 체크 예외는 롤백되지 않는다(`rollbackFor`로
  조정 가능). 다만 이건 단일 리소스(하나의 DB) 기준이고, 서로 다른 두 개 이상의 리소스(예: DB 두 개,
  혹은 DB+JMS)에 걸친 원자성이 필요하면 `JtaTransactionManager`로 2PC(Two-Phase Commit)를 써야 실제로
  보장된다.
- **Consistency**: 제약조건(FK, unique, check) 검증은 DB 엔진이 수행한다. Spring의 역할은 그 검증
  실패로 발생한 예외를 감지해 롤백을 트리거함으로써, 일관성이 깨진 중간 상태가 커밋되지 않도록 막는
  것이다.
- **Isolation**: `@Transactional(isolation = ...)`로 설정한 값이 JDBC
  `Connection.setTransactionIsolation()`으로 그대로 전달된다. 실제 격리(락, MVCC 등)는 DB 엔진이
  수행하며, Spring은 어떤 레벨을 요청할지만 결정한다.
- **Durability**: 트랜잭션 종료 시 Spring이 호출하는 `Connection.commit()`이 실제 트리거이고, 그
  이후 디스크에 안전하게 남기는 작업(WAL/redo log fsync)은 전적으로 DB 엔진의 책임이다. Spring은
  "언제 commit을 부를지" 타이밍만 관리한다.

실무에서 `@Transactional`을 쓰는 가장 직접적인 동기는 "여러 DB 작업을 하나의 실패 단위로 묶어서, 중간에
실패하면 전부 되돌린다"는 것이고, 이게 원자성(Atomicity)이며 그 트랜잭션의 시작~끝 범위가 곧 롤백
단위다. 다만 트랜잭션이 원자성만 제공하는 건 아니다 — 같은 범위 안에서 격리 수준과 지속성도 같이
보장된다. 다만 이 두 가지는 보통 DB 기본값으로 충분히 처리되기 때문에, 체감상 "트랜잭션 = 롤백 범위
설정"으로 느껴지는 것뿐이다.

### 보충: @Transactional 옵션 한눈에

- **propagation**: 전파 방식(3절 참고). 기본값 `Propagation.REQUIRED`.
- **isolation**: 격리 수준(4절 참고). 기본값 `Isolation.DEFAULT`는 데이터소스의 기본 격리 수준을
  그대로 사용.
- **timeout**: 트랜잭션 제한 시간(초). 기본값 -1은 매니저/DB 기본값 사용, 초과 시 예외와 함께 롤백.
- **readOnly**: 읽기 전용 힌트(6절 참고).
- **rollbackFor / rollbackForClassName**: 기본적으로 롤백되지 않는 체크 예외를 롤백 대상으로 추가
  지정(5절 참고).
- **noRollbackFor / noRollbackForClassName**: 기본적으로 롤백되는 `RuntimeException`류 중 롤백 제외할
  예외 지정.
- **transactionManager (value)**: 여러 `PlatformTransactionManager` 빈이 있을 때 사용할 빈 이름 지정.

---

## 2. @Transactional 동작 원리

**Q4. @Transactional은 내부적으로 어떻게 동작하나요?**

스프링 컨테이너가 빈을 등록할 때 `InfrastructureAdvisorAutoProxyCreator` 같은 후처리기가
`@Transactional`이 붙은 빈을 감지해 프록시 객체를 생성한다. 프록시는 실제 대상 호출 전후로
`TransactionInterceptor`를 통해 트랜잭션 매니저의 `getTransaction()`/`commit()`/`rollback()`을
호출하는 어드바이스를 적용한다. 즉 트랜잭션 처리는 비즈니스 로직 바깥에서 프록시가 감싸는 형태로
동작하며, 클라이언트는 프록시를 실제 빈처럼 호출한다.

**Q5. JDK 동적 프록시와 CGLIB 중 스프링부트는 기본적으로 어떤 방식을 사용하나요? 그 이유는?**

스프링부트는 `spring.aop.proxy-target-class` 기본값이 `true`라 기본적으로 CGLIB(클래스 기반)
프록시를 사용한다. 인터페이스가 없는 클래스도 프록시로 만들 수 있게 하기 위함이며, CGLIB은 대상
클래스를 상속한 서브클래스를 생성해 메서드를 오버라이드하는 방식으로 동작한다. 다만 `final` 클래스나
메서드는 오버라이드가 불가능해 프록시가 적용되지 않는다.

**Q6. @Transactional을 private 메서드에 붙이면 왜 동작하지 않나요?**

JDK 프록시와 CGLIB 프록시 모두 외부에서 오버라이드하거나 구현할 수 있는 메서드에만 어드바이스를
적용할 수 있는데, `private` 메서드는 오버라이드가 불가능해 프록시가 가로챌 수 없다. 따라서 애노테이션은
무시되고 트랜잭션 없이 원본 로직이 그대로 실행된다.

**Q7. 같은 클래스 내부에서 this.method()로 트랜잭션 메서드를 호출하면 왜 트랜잭션이 적용되지 않나요?
(self-invocation)**

프록시 객체가 아니라 target 객체 내부에서 `this.method()`를 호출하면 프록시를 거치지 않고 실제
메서드가 바로 호출되어 트랜잭션 어드바이스가 적용되지 않는다. 해결책으로는 자기 자신을 스프링
컨텍스트에서 프록시로 주입받아 호출하거나, 트랜잭션이 필요한 로직을 별도 빈(클래스)으로 분리하거나,
`AopContext.currentProxy()`(`expose-proxy` 설정 필요)를 사용하는 방법이 있다.

### 보충: self-invocation 코드로 보기

트랜잭션이 걸리는 조건은 "호출하는 메서드에 `@Transactional`이 붙어있는가"가 아니라 "그 호출이
프록시를 거치는가"다.

**경우 1: 서로 다른 빈 간의 호출 (정상 동작)**

```java
class OrderController {
    void handle() {
        orderService.placeOrder(); // 다른 빈을 프록시로 호출
    }
}

class OrderService {
    @Transactional
    void placeOrder() { ... } // 정상적으로 트랜잭션 시작됨
}
```

`orderController`는 트랜잭션이 없는 상태지만, `orderService.placeOrder()`는 스프링이 주입해준 프록시
객체를 통해 호출되므로 인터셉터가 정상적으로 가로챈다. 현재 트랜잭션이 없으니 기본값 `REQUIRED`에
따라 새 트랜잭션을 시작한다. 애초에 트랜잭션이 처음 시작되는 지점은 거의 다 이 패턴이다.

**경우 2: 같은 클래스 내부에서 자기 자신을 호출 (self-invocation, 문제되는 케이스)**

```java
class OrderService {
    void doSomething() { // @Transactional 없음
        this.placeOrder(); // 문제: this를 통한 직접 호출
    }

    @Transactional
    void placeOrder() { ... } // 프록시를 안 거쳐서 트랜잭션 안 걸림
}
```

`this.placeOrder()`는 프록시가 아니라 실제 대상 객체(target object)의 메서드를 자바 언어 차원에서
직접 호출하는 것이다. 내부에서 `this`로 호출하면 프록시 레이어를 건너뛰어 버리므로, `placeOrder()`에
`@Transactional`이 붙어 있어도 인터셉터 자체가 실행되지 않고 트랜잭션이 전혀 생기지 않는다.
`doSomething()`에 `@Transactional`이 붙어 있었어도 마찬가지다.

**Q8. @Transactional을 인터페이스에 선언하는 것과 구현 클래스에 선언하는 것의 차이는 무엇인가요?**

인터페이스에 선언하면 JDK 동적 프록시 사용 시에는 인식되지만, CGLIB 프록시(클래스 기반)에서는
인식되지 않을 수 있어 스프링 공식 문서는 구현 클래스에 선언할 것을 권장한다. 클래스 레벨 애노테이션은
해당 클래스의 모든 public 메서드에 적용되며, 메서드 레벨 애노테이션이 클래스 레벨보다 우선 적용된다.

### 보충: 트랜잭션과 자바 스레드풀의 관계

트랜잭션 자체(`BEGIN`)는 아무 락도 걸지 않는다. 그 안에서 실제로 `SELECT ... FOR UPDATE`나 `UPDATE`를
실행할 때 DB 엔진이 격리 수준에 맞춰 필요한 락을 건다. 트랜잭션은 그 락들이 유지되는 범위를 정의하는
것이지, 락 메커니즘 자체는 아니다.

스레드풀과의 연결고리:

- Tomcat 같은 서블릿 컨테이너는 요청 하나당 스레드풀에서 스레드 하나를 꺼내 처리를 맡긴다.
  `@Transactional`이 만든 트랜잭션(그리고 그 안에서 쓰는 `Connection`/`EntityManager`)은
  `ThreadLocal`을 통해 그 요청을 처리하는 스레드에 바인딩된다.
- 요청 A가 스레드 1에서 처리되면 트랜잭션 A는 스레드 1에만 묶이고, 커넥션 풀(HikariCP 등)에서
  커넥션을 하나 빌려 쓴다. 요청 B가 동시에 스레드 2에서 처리되면 완전히 별도의 커넥션·트랜잭션을 쓴다.
- 두 트랜잭션이 같은 row를 건드리면 그때 DB 쪽에서 격리 수준에 따른 락 대기/충돌이 발생한다. 이건
  스레드풀이 만드는 게 아니라, 동시에 실행되는 두 개의 독립적인 DB 트랜잭션이 만드는 현상이다.
- 스레드풀은 스레드를 재사용하므로, 트랜잭션이 끝날 때 반드시 `ThreadLocal`에서 트랜잭션 컨텍스트를
  정리(unbind)해야 다음 요청이 이전 요청의 트랜잭션 상태를 이어받는 사고가 안 생긴다.
  `TransactionInterceptor`가 `finally` 블록에서 이 정리를 해준다.

정리하면, 스레드풀은 "동시에 몇 개의 트랜잭션이 병렬로 뜰 수 있는가"를 결정하는 요인이고, 실제
동시성 제어(락)는 DB가 담당한다. 커넥션 풀 크기도 실질적인 상한선이다 — 스레드가 많아도 풀에 남은
커넥션이 없으면 대기한다.

---

## 3. 전파(Propagation)

**Q9. 트랜잭션 전파 속성(REQUIRED, REQUIRES_NEW, NESTED, SUPPORTS, MANDATORY, NOT_SUPPORTED,
NEVER)을 각각 설명해 주세요.**

**REQUIRED**(기본값)는 기존 트랜잭션이 있으면 참여하고 없으면 새로 시작한다. **REQUIRES_NEW**는 기존
트랜잭션 유무와 상관없이 항상 새 트랜잭션을 시작하며 기존 트랜잭션은 보류(suspend)된다. **NESTED**는
기존 트랜잭션이 있으면 세이브포인트를 만들어 중첩되고, 내부 롤백은 세이브포인트까지만 되돌린다.
**SUPPORTS**는 있으면 참여하고 없으면 트랜잭션 없이 실행한다. **MANDATORY**는 반드시 기존 트랜잭션이
있어야 하며 없으면 예외를 던진다. **NOT_SUPPORTED**는 트랜잭션 없이 실행하고 있으면 보류시킨다.
**NEVER**는 트랜잭션이 있으면 예외를 던진다.

### 각 옵션을 언제 쓰는가

- **REQUIRED**: 대부분의 서비스 메서드에 그냥 기본값으로 쓴다. "하나의 비즈니스 단위는 하나의
  트랜잭션"이라는 일반적인 CRUD·도메인 로직이 여기 해당한다.
- **REQUIRES_NEW**: 감사 로그·알림 발송 이력처럼 "부모가 실패해도 반드시 남겨야 하는 기록"을 저장할
  때, 또는 배치에서 한 건이 실패해도 전체를 롤백시키지 않고 그 건만 별도로 실패 처리·재시도하고 싶을
  때. 커넥션을 하나 더 점유하므로 남용하면 풀 고갈로 이어질 수 있다.
- **NESTED**: 배치 처리에서 일부 항목만 실패해도 그 항목까지만(세이브포인트) 되돌리고 나머지는 계속
  진행하고 싶을 때. `REQUIRES_NEW`와 달리 커넥션은 하나만 쓰므로 자원 부담이 적지만, JDBC
  드라이버·DB가 세이브포인트를 지원해야 동작한다.
- **SUPPORTS**: 트랜잭션이 있어도 되고 없어도 상관없는 읽기 전용 유틸성 조회 메서드에. 트랜잭션 안에서
  호출되면 그대로 참여하고, 단독으로 호출돼도 문제없는 로직에 붙인다.
- **MANDATORY**: 반드시 상위 트랜잭션 안에서만 호출돼야 하는 내부 헬퍼에 방어적으로 붙인다. 실수로
  트랜잭션 없이(예: 배치 잡의 트랜잭션 밖에서) 호출되는 실수를 예외로 조기에 잡아내고 싶을 때 쓴다.
- **NOT_SUPPORTED**: 트랜잭션 범위 안에서 커넥션을 오래 점유하면 안 되는 구간(외부 API 호출, 파일
  I/O)을 잠깐 트랜잭션에서 빼고 싶을 때. `REQUIRES_NEW`처럼 새 트랜잭션을 만들지 않고 그냥 트랜잭션
  없이 실행한다는 점이 다르다.
- **NEVER**: 트랜잭션 컨텍스트 안에서 실행되면 안 되는 로직(예: DDL 실행처럼 오토커밋을 전제하는 코드)에
  방어적으로 붙여, 실수로 트랜잭션 안에서 호출되면 바로 예외로 드러나게 한다.

**Q10. REQUIRED와 REQUIRES_NEW의 차이를 실제 시나리오로 설명해 보세요.**

주문 처리 중 로그를 남기는 경우를 예로 들면, 주문 로직이 실패해 전체가 롤백되더라도 "시도했다"는
로그는 남기고 싶을 수 있다. 이때 로그 저장 메서드를 `REQUIRES_NEW`로 선언하면 별도 트랜잭션·별도
커넥션으로 즉시 커밋되어 로그가 보존된다. `REQUIRED`였다면 로그 저장도 같은 트랜잭션에 묶여 주문
롤백 시 함께 롤백된다.

### 보충: REQUIRED / REQUIRES_NEW 코드로 보기

**REQUIRED — 기존 트랜잭션에 합류**

```java
@Service
public class OrderService {

    private final InventoryService inventoryService;

    @Transactional
    public void placeOrder(Long productId) {
        // (1) 여기서 트랜잭션 시작, 커넥션 A 획득 후 스레드에 바인딩
        orderRepository.save(new Order(productId));

        inventoryService.decreaseStock(productId); // (2) 프록시를 통한 호출

        if (someCondition) {
            throw new RuntimeException("재고 부족"); // (4)
        }
    }
}

@Service
public class InventoryService {

    @Transactional // propagation 기본값 = REQUIRED
    public void decreaseStock(Long productId) {
        // (3) 스레드에 이미 트랜잭션(커넥션 A)이 바인딩되어 있는 걸 감지 → 새로 안 만들고 합류
        stockRepository.decrease(productId);
    }
}
```

흐름: (1) `placeOrder()` 진입 시 `TransactionInterceptor`가 현재 스레드에 트랜잭션이 없는 걸 확인하고
새 트랜잭션(커넥션 A)을 시작해 바인딩한다. (2) `inventoryService.decreaseStock()`을 호출한다. 이
호출도 프록시를 거친다. (3) 인터셉터가 "지금 스레드에 이미 트랜잭션(커넥션 A)이 있다"는 걸 확인하고,
`REQUIRED`이므로 새로 만들지 않고 그 트랜잭션에 합류시킨다. (4) `placeOrder()`에서 예외가 발생하면,
물리적으로 하나의 트랜잭션(커넥션 A)이었으므로 둘 다 롤백된다.

**REQUIRES_NEW — 독립된 새 트랜잭션**

```java
@Service
public class OrderService {

    private final AuditLogService auditLogService;

    @Transactional
    public void placeOrder(Long productId) {
        // (1) 트랜잭션 시작, 커넥션 A
        orderRepository.save(new Order(productId));

        auditLogService.log("주문 시도: " + productId); // (2)

        throw new RuntimeException("결제 실패"); // (4) placeOrder 전체 롤백
    }
}

@Service
public class AuditLogService {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(String message) {
        // (3) 커넥션 A는 잠시 보류(suspend)시키고, 별도의 커넥션 B로 새 트랜잭션 시작
        logRepository.save(new Log(message));
        // 이 메서드가 끝나는 즉시 커넥션 B는 독립적으로 커밋됨
    }
}
```

흐름: (1) `placeOrder()`가 커넥션 A로 트랜잭션 시작. (2) `auditLogService.log()` 호출. (3) 인터셉터가
`REQUIRES_NEW`를 보고, 현재 트랜잭션(커넥션 A)을 잠시 보류시키고 완전히 별도의 새 트랜잭션(커넥션 B)을
만든다. `log()`가 끝나면 커넥션 B는 그 자리에서 바로 커밋되고, 커넥션 A가 재개된다. (4) 이후
`placeOrder()`에서 예외가 나서 커넥션 A(주문 저장)는 롤백되지만, 이미 커밋된 커넥션 B(로그 저장)는
영향을 받지 않는다.

두 케이스 모두 `OrderService.placeOrder`에 `@Transactional`이 있다는 공통점이 있지만, propagation이
실제로 작동하는 지점은 다른 빈의 메서드가 프록시를 통해 호출되는 순간이다.

**Q11. NESTED는 REQUIRES_NEW와 무엇이 다른가요?**

`REQUIRES_NEW`는 완전히 독립된 물리 트랜잭션(별도 커넥션)이라 외부 트랜잭션이 롤백되어도 내부에
영향이 없다(반대도 마찬가지). `NESTED`는 같은 물리 트랜잭션 내에서 세이브포인트를 이용하므로 외부가
롤백되면 내부도 함께 롤백되지만, 내부만 롤백해도 외부는 세이브포인트 이후 지점까지만 되돌리고 계속
진행할 수 있다. 커넥션 자원도 하나만 사용한다는 차이가 있다.

**Q12. 트랜잭션 전파와 관련하여 실무(프로젝트)에서 겪었던 문제나 설계 고민이 있나요?**

경험형 질문이므로 본인 프로젝트와 연결해 구조화된 답변을 준비하는 것이 좋다. 예시 흐름: 여러 테이블에
걸친 배치 작업에서 일부 건이 실패해도 전체를 롤백시키지 않고 실패 건만 별도 트랜잭션으로 재시도하기
위해 `REQUIRES_NEW`를 활용한 경험, 혹은 반대로 트랜잭션을 지나치게 잘게 나눠 `REQUIRES_NEW`를
남용하면 커넥션 풀 고갈이 발생할 수 있어 필요한 곳에만 제한적으로 적용했던 경험 등을 "문제 상황 →
원인 분석 → 선택한 전파 속성과 이유 → 결과"의 흐름으로 답하면 설득력이 높다.

---

## 4. 격리 수준(Isolation Level)

**Q13. 트랜잭션 격리 수준 4단계를 설명하고, 각각에서 발생 가능한 문제를 매핑해 보세요.**

**READ UNCOMMITTED**는 커밋되지 않은 데이터도 읽어 *Dirty Read*가 발생할 수 있다. **READ COMMITTED**는
커밋된 데이터만 읽지만 같은 트랜잭션 내 두 번 읽었을 때 값이 바뀔 수 있어 *Non-repeatable Read*가
발생한다. **REPEATABLE READ**는 트랜잭션 시작 시점 스냅샷을 유지해 같은 행을 반복 읽어도 동일한 값을
보장하지만, 새로 삽입된 행까지는 막지 못해 *Phantom Read*가 이론상 발생할 수 있다(다만 MySQL InnoDB는
갭 락으로 이를 상당 부분 방지한다). **SERIALIZABLE**은 트랜잭션을 순차 실행한 것과 같은 효과로 가장
안전하지만 동시성이 가장 낮다.

### 보충: 격리 수준별 이상현상 표

| 격리 수준 | Dirty Read | Non-repeatable Read | Phantom Read |
|---|---|---|---|
| READ_UNCOMMITTED | 발생 가능 | 발생 가능 | 발생 가능 |
| READ_COMMITTED | 방지 | 발생 가능 | 발생 가능 |
| REPEATABLE_READ | 방지 | 방지 | 표준상 발생 가능 (MySQL InnoDB는 갭 락으로 대부분 방지) |
| SERIALIZABLE | 방지 | 방지 | 방지 |

이상현상 정의:

- **Dirty Read**: 다른 트랜잭션이 아직 커밋 안 한 데이터를 읽어버리는 것. 그 트랜잭션이 롤백되면
  존재한 적 없는 값을 읽은 셈이 된다.
- **Non-repeatable Read**: 같은 트랜잭션 안에서 같은 row를 두 번 읽었는데, 그 사이 다른 트랜잭션이
  그 row를 수정·커밋해서 값이 달라져 있는 것.
- **Phantom Read**: 같은 조건으로 두 번 조회했는데, 그 사이 다른 트랜잭션이 새 row를 insert(또는
  delete)해서 조회 결과의 행 개수가 달라지는 것.

격리 수준별 실제 구현 차이:

- **READ_UNCOMMITTED**: 커밋 안 된 데이터도 그냥 읽음. 실무에서 거의 안 씀(Oracle은 지원조차 안 하고
  READ_COMMITTED로 동작).
- **READ_COMMITTED**: 커밋된 데이터만 읽도록 강제. Oracle, PostgreSQL, SQL Server의 기본값.
- **REPEATABLE_READ**: 트랜잭션 시작(혹은 첫 조회) 시점의 스냅샷을 트랜잭션 끝까지 유지. MySQL
  InnoDB의 기본값이며, 갭 락(gap lock)까지 추가로 걸어서 표준 정의보다 더 강하게 Phantom Read를
  막아준다.
- **SERIALIZABLE**: 트랜잭션들이 순차 실행된 것과 동일한 결과를 강제. 범위 락(range lock) 수준까지
  걸리므로 동시성이 크게 떨어지고 데드락 위험도 커진다.
- **DEFAULT**: Spring이 별도 지정 안 하고 데이터소스의 기본 격리 수준을 그대로 사용.

**Q14. MySQL(InnoDB)의 기본 격리 수준은 무엇이며, 스프링의 기본값과 어떤 관계가 있나요?**

MySQL InnoDB의 기본 격리 수준은 `REPEATABLE READ`다. 스프링(`JpaTransactionManager`/
`DataSourceTransactionManager`)의 기본 `Isolation`은 `DEFAULT`로, 이는 별도 지정 없이 사용 중인 DB의
기본 격리 수준을 그대로 따르는 것을 의미한다. 즉 MySQL을 쓰면 결과적으로 `REPEATABLE READ`가 적용된다.

**Q15. 격리 수준을 높이면 어떤 트레이드오프가 발생하나요?**

격리 수준을 높일수록 데이터 일관성·정합성은 강해지지만 락 경합이 늘어나 동시 처리량(TPS)이 떨어지고
데드락 가능성도 커진다. 반대로 낮추면 성능은 좋아지지만 Dirty/Non-repeatable/Phantom Read 같은
이상현상 위험이 커진다. 실무에서는 기본값을 유지하고, 문제가 되는 특정 쿼리에만 락 힌트나 격리 수준을
조정하는 방식을 선호한다.

---

## 5. 롤백 규칙과 예외 처리

**Q16. 스프링은 기본적으로 어떤 예외에서 롤백하고, 어떤 예외에서는 롤백하지 않나요?**

스프링 기본 정책은 `RuntimeException`(언체크 예외)과 `Error`가 발생하면 롤백하고, 체크 예외
(`Exception`을 상속하지만 `RuntimeException`이 아닌 것)는 롤백하지 않고 커밋한다. 이는 체크 예외를
"비즈니스적으로 예상 가능한 대안 흐름"으로 보던 관례를 스프링이 그대로 계승한 것이다.

**Q17. rollbackFor, noRollbackFor 속성은 언제 사용하나요?**

체크 예외인데도 롤백시키고 싶으면 `@Transactional(rollbackFor = SomeCheckedException.class)`를,
반대로 런타임 예외인데 롤백시키고 싶지 않으면 `noRollbackFor`를 사용한다. 클래스를 지정하면 해당
예외의 하위 클래스도 함께 대상에 포함된다.

**Q18. 체크 예외를 롤백 대상으로 만들고 싶을 때 어떻게 처리하나요?**

`rollbackFor` 속성을 지정하거나, 체크 예외를 언체크 예외로 감싸(wrap) 던지는 방식을 사용한다. 팀
컨벤션에 따라 아예 체크 예외 사용을 지양하고 런타임 예외 계층만 사용하는 경우도 실무에서 흔하다.

**Q19. 트랜잭션 메서드 안에서 예외를 잡아서(catch) 처리하면 왜 롤백이 안 되나요?**

트랜잭션 AOP는 대상 메서드가 예외를 밖으로 던져야만 그 예외를 보고 롤백 여부를 판단하는데, 메서드
내부에서 예외를 catch해 삼켜버리면 프록시 입장에서는 메서드가 정상 종료된 것으로 보여 커밋을
수행한다. 롤백시키려면 예외를 다시 던지거나, `TransactionAspectSupport.currentTransactionStatus()
.setRollbackOnly()`를 명시적으로 호출해야 한다.

---

## 6. JPA 영속성 컨텍스트

**Q20. JPA의 영속성 컨텍스트와 트랜잭션의 생명주기는 어떤 관계가 있나요?**

JPA는 트랜잭션 시작 시 영속성 컨텍스트(1차 캐시)를 생성하고, 트랜잭션 커밋 시점에 flush(변경 감지 후
UPDATE/INSERT/DELETE SQL 발행)와 실제 커밋을 함께 수행한다. 스프링은 `JpaTransactionManager`가
`EntityManager`를 트랜잭션 동기화 매니저에 바인딩해, 같은 트랜잭션 내 여러 리포지토리 호출이 같은
영속성 컨텍스트를 공유하도록 한다.

**Q21. OSIV(Open Session In View)가 무엇이며, 켜져 있을 때와 꺼져 있을 때의 장단점은?**

OSIV(스프링부트 기본값 `true`)가 켜져 있으면 트랜잭션이 끝난 뒤에도 뷰 렌더링 시점까지 영속성
컨텍스트와 커넥션이 살아있어 컨트롤러나 뷰에서도 지연 로딩이 가능하지만, 커넥션을 오래 점유해 커넥션
풀 고갈 위험이 있다. 꺼두면(`false`) 트랜잭션 종료와 함께 영속성 컨텍스트가 닫혀 커넥션 반환은
빨라지지만, 트랜잭션 밖에서 지연 로딩 시 `LazyInitializationException`이 발생하므로 서비스 계층에서
필요한 데이터를 fetch join 등으로 미리 로딩해야 한다.

**Q22. @Transactional(readOnly = true)는 실제로 어떤 최적화를 가능하게 하나요?**

하이버네이트 세션의 flush 모드를 MANUAL에 가깝게 바꿔 변경 감지(dirty checking)를 위한 스냅샷 비교
로직을 생략시켜 성능을 아낀다. 마스터/슬레이브 구조 등에서 읽기 전용 복제본으로 연결을 라우팅하는
데도 활용될 수 있다. 다만 `readOnly`는 강제 규약이 아니라 힌트이므로, 실수로 저장 로직을 넣어도
컴파일 타임에 막아주지는 않는다.

### 보충: readOnly가 정확히 하는 일 — Dirty Read와 Dirty Checking 구분

`@Transactional(readOnly = true)`는 실제로 두 가지를 한다.

1. **Flush 모드를 MANUAL로 변경**: `JpaTransactionManager`가 트랜잭션 시작 시 `readOnly=true`를 보면
   Hibernate `Session`의 flush 모드를 `MANUAL`로 바꾼다. 원래 Hibernate는 쿼리를 날리기 직전이나
   트랜잭션 커밋 직전에 변경된(dirty) 엔티티가 있으면 자동으로 flush(UPDATE 실행)하는데,
   `readOnly=true`면 이 자동 flush 자체를 안 하게 만든다.
2. **엔티티를 read-only로 마킹**: Hibernate는 원래 로딩한 엔티티마다 스냅샷을 별도로 들고 있다가
   flush 시점에 비교해서 변경분을 찾는데, `readOnly=true`면 이 스냅샷 보관 자체를 생략해서 메모리
   사용량과 비교 비용을 줄인다. 이 때문에 이 상태에서 필드를 바꿔도 조용히 DB에 반영되지 않는다
   (예외 없이 무시됨).

추가로 Spring이 JDBC 커넥션에 `Connection.setReadOnly(true)`를 호출하기도 하는데, 이건 DB/드라이버
구현에 따라 다르게 활용된다(읽기 전용 복제본으로 라우팅하는 힌트 등).

**readOnly의 위험은 격리 수준(Dirty Read)과 무관하다.** "Dirty Read"(다른 트랜잭션의 커밋 안 된
데이터를 읽는 것)와 "Dirty Checking"(내 영속성 컨텍스트 안에서 변경분을 찾아내는 것)은 이름만 비슷할
뿐 전혀 다른 개념이다. MySQL이 REPEATABLE_READ로 Dirty Read를 막아주는 것은 readOnly의 안전성과
아무 관계가 없다.

readOnly가 안전한지 판단하는 기준은 단순하다: **그 메서드가 정말로 아무것도 쓰지(write) 않는 순수
조회 메서드인가**만 보면 된다. 맞다면 안전하고 성능상 이득이 있다. 조회 도중 일부라도 쓰기가 섞여
있다면, 그 변경사항이 예외 없이 조용히 반영 안 되는 버그가 생길 수 있으므로 붙이면 안 된다.

**Q23. 트랜잭션 커밋 시점에 영속성 컨텍스트의 변경 감지(Dirty Checking)와 플러시가 어떻게 일어나는지
설명해 주세요.**

트랜잭션 커밋 직전 JPA가 자동으로 flush를 호출해, 영속성 컨텍스트 내 엔티티들을 최초 로딩 시점의
스냅샷과 비교(dirty checking)한다. 변경된 필드가 있으면 UPDATE 쿼리를 생성해 DB에 반영한 뒤 실제
commit을 수행한다. 명시적으로 save()를 호출하지 않아도 영속 상태 엔티티의 필드를 변경하기만 하면
커밋 시 자동 반영되는 이유가 바로 이 메커니즘 때문이다.

### 보충: flush가 트리거되는 세 가지 시점

"메서드 종료 시" 자동으로 flush된다고 체감하기 쉽지만, 정확히는 flush가 다음 세 가지 경우에
트리거되는 것이고 메서드 종료(커밋)는 그중 하나일 뿐이다.

1. **트랜잭션 커밋 직전**: `@Transactional` 메서드가 정상 종료되어 Spring이
   `transactionManager.commit()`을 호출하면, 실제 물리적 커밋 전에 Hibernate가 자동으로 flush를 한 번
   수행한다.
2. **쿼리 실행 직전**: 기본 flush 모드(`AUTO`)에서는 트랜잭션이 끝나기 전이라도 쿼리를 날리는 순간,
   그 결과가 지금까지의 변경사항과 어긋나지 않도록 먼저 flush부터 하고 쿼리를 실행한다.
3. **`entityManager.flush()` 수동 호출**.

**Q24. 지연 로딩(Lazy Loading)이 트랜잭션 범위를 벗어나면 왜 LazyInitializationException이 발생하나요?**

지연 로딩 필드는 실제 접근 시점에 영속성 컨텍스트(세션)를 통해 추가 쿼리로 로딩되는데, 트랜잭션이
이미 종료돼 세션이 닫힌 상태에서 접근하면 초기화가 불가능해 예외가 발생한다. 해결책으로는 fetch
join·`@EntityGraph`로 미리 로딩하거나, 필요한 시점까지 트랜잭션 범위를 넓히거나(OSIV), 트랜잭션
안에서 필요한 데이터를 DTO로 미리 변환해 반환하는 방법이 있다.

---

## 7. 트랜잭션 매니저

**Q25. PlatformTransactionManager, JpaTransactionManager, DataSourceTransactionManager의 차이와
관계를 설명해 주세요.**

`PlatformTransactionManager`는 스프링 트랜잭션 추상화의 최상위 인터페이스다. `DataSourceTransactionManager`는
순수 JDBC/MyBatis 등에서 하나의 `DataSource` 커넥션 트랜잭션을 관리한다. `JpaTransactionManager`는
`EntityManagerFactory`를 기반으로 JPA 영속성 컨텍스트와 트랜잭션을 함께 관리하며, 내부적으로
`DataSourceTransactionManager`의 기능도 포함하므로 JPA를 쓰면 보통 이것을 사용한다.

**Q26. 여러 개의 데이터소스를 사용할 때 트랜잭션 매니저는 어떻게 구성하나요?**

데이터소스별로 별도의 `PlatformTransactionManager` 빈을 등록하고, `@Transactional(transactionManager
= "beanName")`으로 어떤 매니저를 쓸지 명시한다. 서로 다른 데이터소스에 걸쳐 하나의 원자적 트랜잭션이
필요하다면 로컬 트랜잭션 매니저 여러 개로는 원자성을 보장할 수 없으므로 JTA(분산 트랜잭션) 매니저가
필요하다.

**Q27. JTA(분산 트랜잭션 매니저)는 언제 필요한가요?**

서로 다른 두 개 이상의 리소스(예: 별도의 두 DB, 혹은 DB와 메시지 큐)에 대해 하나의 트랜잭션으로
원자적 커밋/롤백을 보장해야 할 때 필요하다. 다만 2PC 기반 JTA는 성능·운영 복잡도가 크므로, 실무에서는
가능하면 아키텍처를 바꿔(단일 DB로 통합, 이벤트 기반 최종 일관성 등) JTA를 피하는 방향을 우선
고려한다.

---

## 8. 락(Lock)과 동시성

**Q28. 낙관적 락(Optimistic Lock)과 비관적 락(Pessimistic Lock)의 차이와 구현 방법은?**

**낙관적 락**은 충돌이 드물다고 가정하고, 실제 갱신 시점에 버전(`@Version` 컬럼)을 비교해 다른
트랜잭션이 먼저 수정했으면 `OptimisticLockException`을 발생시켜 애플리케이션이 재시도하게 한다.
**비관적 락**은 조회 시점에 DB 락(`SELECT ... FOR UPDATE`, JPA는 `LockModeType.PESSIMISTIC_WRITE`)을
걸어 다른 트랜잭션의 접근 자체를 대기시킨다. 충돌 빈도가 낮고 재시도가 가능한 도메인은 낙관적 락을,
재고 차감처럼 충돌이 잦고 정합성이 중요한 곳은 비관적 락을 주로 사용한다.

**Q29. 데드락(Deadlock)이 발생하는 상황을 설명하고, 방지 방법을 말해 주세요.**

두 트랜잭션이 서로 다른 순서로 여러 자원(행)에 락을 걸며, 서로가 상대방이 점유한 락을 기다리는 순환
대기 상태에서 발생한다. 방지 방법으로는 여러 자원에 접근할 때 항상 동일한 순서로 락을 획득하도록
코드 규칙을 정하거나, 트랜잭션·락 범위를 최소화하고, 타임아웃(`lock_wait_timeout`)을 설정해 무한
대기 대신 실패시킨 뒤 재시도 로직으로 처리하는 방법이 있다.

**Q30. 트랜잭션 범위를 짧게 유지해야 하는 이유는 무엇인가요?**

트랜잭션이 길어질수록 커넥션 풀에서 커넥션을 오래 점유해 다른 요청이 커넥션을 받지 못해 대기(풀
고갈)하게 되고, DB 락도 그만큼 오래 걸려 있어 동시성 처리량이 떨어진다. 따라서 외부 API 호출이나
파일 I/O처럼 느린 작업은 트랜잭션 밖으로 빼고, 순수 DB 작업만 트랜잭션 안에 최소한으로 묶는 것이
원칙이다.

---

## 9. 분산 트랜잭션 · MSA

**Q31. 마이크로서비스 환경에서 여러 서비스에 걸친 트랜잭션은 어떻게 처리하나요?**

각자 자기 DB를 소유한 여러 서비스에 걸친 하나의 강한 원자적 트랜잭션은 2PC(분산 트랜잭션)로 이론상
가능하지만 가용성·성능 문제로 잘 쓰지 않는다. 대신 각 단계를 로컬 트랜잭션으로 처리하고 실패 시
보상 트랜잭션으로 되돌리는 **Saga 패턴**(Choreography 또는 Orchestration 방식)이나, Outbox 패턴과
메시지 브로커를 결합한 **이벤트 기반 최종 일관성**을 주로 사용한다.

**Q32. @Transactional이 걸린 메서드 안에서 외부 API(HTTP) 호출을 하면 어떤 문제가 생길 수 있나요?**

외부 API 응답이 느리면 그동안 DB 커넥션과 트랜잭션이 계속 점유돼 커넥션 풀 고갈과 락 경합 증가로
이어진다. 또한 외부 API 호출은 트랜잭션 롤백으로 되돌릴 수 없는 부수효과이므로, 호출 뒤 DB 작업이
실패해 롤백돼도 이미 외부 시스템에는 반영된 상태가 되어 데이터 불일치가 생길 수 있다. 트랜잭션 커밋
이후에 외부 호출을 하도록 설계하는 것이 안전하다.

**Q33. 트랜잭션 커밋 이후에만 특정 이벤트(메시지 발행 등)를 실행하고 싶다면 어떻게 구현하나요?**

`ApplicationEventPublisher`로 이벤트를 발행하고, 리스너에 `@TransactionalEventListener(phase =
TransactionPhase.AFTER_COMMIT)`를 붙이면 트랜잭션이 성공적으로 커밋된 이후에만 해당 리스너가
실행된다. 트랜잭션이 롤백되면 이벤트 리스너는 실행되지 않아, 롤백될 작업의 부수효과가 외부로
새어나가는 것을 방지할 수 있다.

---

## 10. 테스트와 트러블슈팅

**Q34. 테스트 코드에서 @Transactional을 사용하면 왜 기본적으로 롤백되나요? 실제 코드와 다르게 동작할
수 있는 함정은?**

테스트 클래스나 메서드에 `@Transactional`을 붙이면 스프링 테스트 프레임워크가 각 테스트를 트랜잭션으로
감싸고, 종료 후 기본적으로 자동 롤백시켜 DB 상태를 오염시키지 않고 반복 실행할 수 있게 한다. 함정은,
실제 운영 코드에서는 발생하지 않는데 테스트에서는 같은 트랜잭션·영속성 컨텍스트를 공유하기 때문에
지연 로딩이 우연히 성공하거나, `REQUIRES_NEW`로 별도 커밋되어야 할 로직이 테스트에서는 여전히 마지막에
롤백되어버리는 등 실제 동작과 다르게 관찰될 수 있다는 점이다.

**Q35. 트랜잭션이 걸린 메서드에서 @Async를 함께 사용하면 어떤 문제가 발생할 수 있나요?**

`@Async`는 별도 스레드에서 메서드를 실행하는데, 스프링 트랜잭션은 `ThreadLocal` 기반
(`TransactionSynchronizationManager`)으로 현재 스레드에 커넥션·트랜잭션 정보를 바인딩한다. 따라서
비동기로 실행되는 스레드는 호출한 트랜잭션을 이어받지 못하고 새로운(또는 트랜잭션 없는) 컨텍스트에서
실행된다. 두 애노테이션을 같은 메서드에 함께 붙이면, 비동기 로직이 원본 트랜잭션의 커밋 전에 먼저
실행돼 아직 반영되지 않은 데이터를 읽는 등 예측하지 못한 동작이 발생할 수 있다.

**Q36. 커넥션 풀(HikariCP) 고갈 문제와 트랜잭션 관리는 어떻게 연결되나요?**

트랜잭션이 커넥션 하나를 점유하는 시간이 길어지면(느린 쿼리, 트랜잭션 안 외부 호출, OSIV로 인한 뷰
렌더링까지의 커넥션 유지 등) 동시 요청이 몰릴 때 풀의 모든 커넥션이 사용 중이 되어, 신규 요청이
커넥션을 받지 못하고 대기하다 타임아웃되는 문제로 이어진다. 대응으로는 트랜잭션 범위 최소화, OSIV
비활성화 검토, 느린 쿼리 최적화, HikariCP의 `maximum-pool-size`·`connection-timeout` 튜닝, active/idle
커넥션 수 모니터링을 함께 진행한다.

**Q37. 대용량 배치 작업에서 트랜잭션을 어떻게 설계했는지, 왜 그렇게 설계했는지 설명해 보세요.**

대량 데이터를 하나의 트랜잭션으로 처리하면 영속성 컨텍스트에 엔티티가 계속 쌓여 메모리 문제가 생기고,
트랜잭션·락 점유 시간도 길어진다. 그래서 일정 개수(청크) 단위로 트랜잭션을 나눠 커밋하고 영속성
컨텍스트를 주기적으로 flush/clear하는 방식(예: 스프링 배치의 청크 지향 처리)을 사용한다. 이렇게 하면
중간에 실패해도 이미 커밋된 청크는 유지되고, 실패 지점부터 재시작할 수 있는 설계도 가능해진다.

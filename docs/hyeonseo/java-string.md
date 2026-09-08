# Java String / StringBuilder 개념 정리

`char`와 `String`의 관계, `String`이 불변(immutable)인 이유, 문자열 연결(concatenation) 방법들,
`StringBuilder`와 `StringBuffer`의 차이를 정리.

## 목차

1. [char와 String](#1-char와-string)
2. [String 리터럴과 new String()의 차이](#2-string-리터럴과-new-string의-차이)
3. [String이 immutable인 이유와 장단점](#3-string이-immutable인-이유와-장단점)
4. [String concatenation의 여러 방법](#4-string-concatenation의-여러-방법)
5. [String + 연산 vs StringBuilder](#5-string--연산-vs-stringbuilder)
6. [StringBuilder의 주요 메서드](#6-stringbuilder의-주요-메서드)
7. [StringBuilder vs StringBuffer](#7-stringbuilder-vs-stringbuffer)
8. [String vs StringBuffer vs StringBuilder 비교표](#8-string-vs-stringbuffer-vs-stringbuilder-비교표)

---

## 1. char와 String

**Q. char와 String의 메모리 사용 차이는 무엇인가요?**

`char`는 자바의 기본형(primitive type)이다. UTF-16 코드 유닛 하나를 표현하는 2바이트 값 그 자체이며,
지역변수라면 스택에, 객체 필드라면 그 객체 안에 인라인으로 저장된다. 별도의 객체 헤더나 참조 오버헤드가
없다.

```java
char capitalA = 'A';          // 값 그 자체(2바이트)가 저장됨
String a = "abc";             // 문자의 배열(시퀀스)을 감싼 객체에 대한 참조가 저장됨
String b = new String("abc");
```

`String`은 참조형(reference type)이다. 문자열은 자바 프로그램이 실행되는 동안 가장 많이 생성되는
객체 중 하나인데, 객체이지만 내부적으로는 `char`(JDK 9 이전) 또는 `byte[]` + 인코딩 플래그(JDK 9+
Compact Strings)의 시퀀스로 구성된다. 즉 `char` 하나는 값 자체가 메모리를 차지하는 반면, `String`은
객체 헤더 + 내부 배열에 대한 참조 + 캐시된 해시코드 필드 등 훨씬 많은 메모리 오버헤드를 가진 힙 객체다.

---

## 2. String 리터럴과 new String()의 차이

```java
public void testString() throws Exception {
    String a = "abc";
}

public void testNewString() throws Exception {
    String b = new String("abc");
}
```

`String a = "abc"`처럼 문자열 리터럴로 선언하면, JVM은 **String Constant Pool**(문자열 상수 풀)이라는
별도 영역을 먼저 확인한다. 같은 내용의 문자열이 이미 풀에 있으면 새로 만들지 않고 그 객체를 그대로
재사용하고, 없으면 풀에 새로 등록한다. 반면 `new String("abc")`는 문자열 리터럴 `"abc"`가 풀에 등록되는
것과 별개로, 힙에 **항상 새로운 String 객체**를 하나 더 만든다(내용은 같지만 `==` 비교로는 다른 객체).

이 차이가 실무적으로 의미가 있는 이유는, 문자열이 프로그램에서 가장 많이 생성되는 객체이기 때문이다.
같은 리터럴을 반복해서 쓰는 코드(예: 상수 문자열)라면 리터럴 방식이 풀 재사용 덕분에 메모리를 아낄
수 있고, `new String(...)`을 굳이 남발하면 불필요한 객체가 힙에 계속 쌓인다.

---

## 3. String이 immutable인 이유와 장단점

**Q. String 객체가 immutable인 이유와 장단점은 무엇인가요?**

**Immutable(불변)**이란 객체를 생성한 후 상태를 변경할 수 없는 것을 의미한다. `String` 객체는 한 번
만들어지면 그 안의 문자열 값을 바꿀 수 있는 방법이 없다 — `String`의 메서드(`replace`, `substring`,
`toUpperCase` 등)는 모두 원본을 바꾸지 않고 **새로운 String 객체를 반환**할 뿐이다.

**불변으로 설계된 이유**:
- **String Constant Pool 재사용**: 여러 변수가 같은 리터럴을 공유해도, 값이 바뀔 수 없으니 안전하게
  같은 객체를 공유시킬 수 있다. 가변 객체였다면 한쪽에서 값을 바꿨을 때 그걸 공유하던 다른 모든
  변수도 같이 바뀌는 사고가 난다.
- **해시코드 캐싱**: `String`은 내부적으로 해시코드를 한 번 계산해 캐시해둔다. 값이 절대 안 바뀌므로
  이 캐시가 영원히 유효하다 — `HashMap`의 키로 `String`이 자주 쓰이는 이유 중 하나다(매번 해시를
  재계산할 필요가 없음).
- **스레드 안전성**: 값이 바뀌지 않으니 여러 스레드가 같은 `String` 객체를 동기화 없이 공유해도
  안전하다.
- **보안**: 클래스 로더가 클래스 이름을, 네트워크 연결이 호스트 주소를 `String`으로 받는 경우가
  많은데, 만약 가변이었다면 그 값을 넘긴 뒤 몰래 바꿔치기하는 공격이 가능해진다.

**단점**: 문자열을 반복해서 수정·연결하는 상황에서는 매번 새 객체가 만들어지므로 메모리 할당과 GC
부담이 커진다. 이 단점을 보완하기 위한 게 바로 `StringBuilder`/`StringBuffer`다(5절).

---

## 4. String concatenation의 여러 방법

**Q. 문자열을 연결(concatenation)하는 방법에는 어떤 것들이 있나요?**

- **`+` 연산자**: 가장 직관적이지만, `String`이 불변이라 매번 새 객체를 만든다. 단, 컴파일러가 단일
  구문 안의 `+` 연산은 내부적으로 `StringBuilder`로 최적화해주는 경우가 많다. 다만 **반복문 안에서**
  `+`를 쓰면 매 반복(iteration)마다 새 `StringBuilder`가 생성되므로 이 최적화 혜택을 못 받고
  느려진다.
- **`String.concat(String)`**: `a.concat(b)` 형태. 내부적으로도 새 배열을 만들어 복사하는 방식이라
  `+`와 성능 특성이 비슷하다.
- **`StringBuilder`/`StringBuffer`의 `append()`**: 내부 버퍼에 이어 붙이는 방식이라, 여러 번 연결해야
  하는 경우 가장 효율적이다(5절).
- **`String.format(String, Object...)`**: 형식 문자열에 값을 끼워 넣는 방식. 가독성은 좋지만 내부적으로
  파싱 비용이 있어 단순 연결보다 느리다.
- **`String.join(CharSequence, CharSequence...)`**: 구분자로 여러 문자열을 이어 붙일 때 쓴다.
- **`StringJoiner`**: `String.join`보다 유연하게(접두사/접미사 포함) 구분자 기반 연결을 할 때 쓴다.

---

## 5. String + 연산 vs StringBuilder

`String`은 불변(immutable)이기 때문에 `String`과 `String`을 더하면 **새로운 String 객체를 생성**한다.
따라서 `String`과 `String`을 더하는 시점마다 메모리 할당과 (이전 임시 객체의) 메모리 해제가 계속
발생한다.

`StringBuilder`는 `String`과 다르게 **기존 데이터에 새로운 데이터를 이어 붙이는 방식**을 취하기
때문에 속도가 더 빠르다. 내부에 가변 `char[]`(또는 `byte[]`) 버퍼를 들고 있다가, 버퍼가 꽉 차면
용량을 늘려가며 재사용한다 — 매 연결마다 완전히 새로운 객체를 만드는 `String`과 근본적으로 다르다.

따라서 **긴 문자열을 반복해서 더하는 상황**(반복문 안에서 문자열을 조립하는 경우 등)에서는
`StringBuilder`를 활용해 구현하는 것이 원칙이다.

---

## 6. StringBuilder의 주요 메서드

**Q. StringBuilder의 주요 메서드를 설명해 주세요.**

- **`append(...)`**: 인자로 받은 값(문자열, 문자, 숫자 등 다양한 타입 오버로드)을 버퍼 끝에 이어
  붙인다. `+` 연산자 대신 이 메서드를 쓰는 것이 `StringBuilder`를 쓰는 핵심 이유다.
  ```java
  StringBuilder sb = new StringBuilder();
  sb.append("코드스쿼드 백엔드 수강생은? ").append(14).append(" 명이다.");
  // sb.toString() == "코드스쿼드 백엔드 수강생은? 14 명이다."
  ```
- **`insert(int offset, ...)`**: 지정한 위치(offset)에 값을 끼워 넣는다. `append`가 항상 끝에
  붙이는 것과 달리 임의 위치 삽입이 필요할 때 쓴다.
- **`delete(int start, int end)` / `deleteCharAt(int index)`**: 지정한 구간(또는 한 글자)을 버퍼에서
  제거한다.
- 이 외에도 `reverse()`(문자열 뒤집기), `replace(int start, int end, String str)`(구간 치환),
  `toString()`(최종 불변 `String`으로 변환) 등이 자주 쓰인다.

---

## 7. StringBuilder vs StringBuffer

두 클래스는 API가 거의 동일하다(`append`, `insert`, `delete`, `reverse` 등 메서드 시그니처가 사실상
같다). 둘 다 `String`과 달리 **가변(mutable)** 객체이고, 내부에 크기가 자동으로 늘어나는 버퍼(`char[]`
또는 `byte[]`)를 들고 있다가 그 버퍼에 직접 데이터를 추가·삭제한다 — 그래서 4~5절에서 본 것처럼 문자열
연결/조립을 반복해도 매번 새 객체를 만드는 `String`과 달리 기존 객체 하나만 계속 재사용한다.

### 7.1 StringBuilder — 개념과 주로 쓰이는 상황

**개념**: Java 5에서 추가된, **동기화(`synchronized`)가 없는** 가변 문자열 클래스. 스레드 안전을
보장하지 않는 대신 메서드 호출마다 락을 걸고 푸는 비용이 없어 `StringBuffer`보다 빠르다.

**주로 쓰이는 상황**:
- 메서드 안의 **지역 변수**로 문자열을 조립할 때(가장 흔한 경우) — 애초에 한 스레드 안에서만
  쓰이므로 동기화가 무의미하다.
- 반복문 안에서 문자열을 반복적으로 이어 붙여야 할 때(로그 메시지 조립, CSV/JSON 문자열 수동 생성 등).
- 사실 자바 컴파일러가 `String a = "x" + b + "y";`처럼 한 구문 안의 `+` 연산을 내부적으로
  `StringBuilder`로 자동 변환해주는데, 이때도 항상 `StringBuilder`를 쓴다(동기화가 필요 없는 지역적
  연산이므로).
- **실무 기본값**: 멀티스레드에서 같은 인스턴스를 공유해서 조작해야 하는 특수한 경우가 아니라면
  `StringBuffer` 대신 항상 이걸 먼저 고려한다.

### 7.2 StringBuffer — 개념과 주로 쓰이는 상황

**개념**: JDK 1.0부터 있던, `append`/`insert`/`delete` 등 **모든 메서드가 `synchronized`로 선언된**
가변 문자열 클래스. 여러 스레드가 동시에 같은 인스턴스를 조작해도 안전하지만, 매 호출마다 락을 걸고
푸는 동기화 비용이 든다.

**주로 쓰이는 상황**:
- **여러 스레드가 실제로 같은 `StringBuffer` 인스턴스를 공유하며 동시에 값을 추가/수정해야 하는
  경우**(예: 여러 워커 스레드가 하나의 공유 로그 버퍼에 계속 문자열을 append하는 구조). 이런 경우가
  아니면 동기화 비용만 손해다.
- Java 5 이전(제네릭·`StringBuilder` 자체가 없던 시절)에 작성된 레거시 코드 — 당시엔 가변 문자열
  클래스가 `StringBuffer` 하나뿐이었다.
- 신규 코드에서는 "정말로 스레드 간 공유가 필요한지"를 먼저 따져보고, 아니라면 `StringBuilder`를
  쓰는 것이 정석이다.

### 7.3 왜 StringBuilder가 더 효율적인가

**Q. StringBuilder와 StringBuffer의 차이는 무엇이고, StringBuilder가 왜 더 효율적인가요?**

차이는 **스레드 안전성** 하나뿐이다. `StringBuilder`가 더 효율적인 이유는 결국 **대부분의 문자열
조립은 메서드 지역 변수처럼 한 스레드 안에서만 쓰이고, 여러 스레드가 동시에 공유할 필요가 없다**는
데 있다. 그런 상황에서 `StringBuffer`의 동기화는 아무 이득 없이 성능만 깎아먹는 비용이 된다. 그래서
실무에서는 **멀티스레드 환경에서 같은 버퍼를 공유해서 조작해야 하는 특수한 경우가 아니라면
`StringBuilder`를 기본으로 쓰고**, 정말 동기화가 필요할 때만 `StringBuffer`를 쓴다.

---

## 8. String vs StringBuffer vs StringBuilder 비교표

| 구분 | String | StringBuffer | StringBuilder |
|---|---|---|---|
| 가변성(mutable) | 불변(immutable) — 수정 시마다 새 객체 생성 | 가변 — 내부 버퍼를 직접 수정 | 가변 — 내부 버퍼를 직접 수정 |
| 스레드 안전성 | 안전(값이 안 바뀌므로 공유해도 문제 없음) | 안전(모든 메서드 `synchronized`) | **안전하지 않음**(동기화 없음) |
| 동기화 오버헤드 | 해당 없음 | 있음(메서드 호출마다 락) | 없음 |
| 성능(단일 스레드 반복 연결 기준) | 가장 느림(매번 새 객체 + GC 대상 증가) | 중간(락 오버헤드) | **가장 빠름** |
| 등장 시점 | JDK 1.0 | JDK 1.0 | Java 5(JDK 1.5) |
| 주 사용처 | 값이 거의 안 바뀌는 문자열, `Map`의 키, 상수 등 | 멀티스레드에서 같은 버퍼를 실제로 공유해 조작할 때 | 단일 스레드에서 문자열을 반복 조립할 때(가장 흔한 경우) |
| 내부 구조 | 내부 배열(`char[]`/`byte[]`) 참조, 한 번 만들면 안 바뀜 | 가변 크기 버퍼(`char[]`/`byte[]`) + 동기화 메서드 | 가변 크기 버퍼(`char[]`/`byte[]`), 동기화 없음 |

**한 줄 요약**: 값이 안 바뀌면 `String`, 값이 자주 바뀌는데 여러 스레드가 동시에 공유하면
`StringBuffer`, 값이 자주 바뀌는데 한 스레드에서만 쓰면(대부분의 경우) `StringBuilder`.

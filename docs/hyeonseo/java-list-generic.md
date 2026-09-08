# Java List와 Generic 개념 정리

`List` 인터페이스와 대표 구현체(`ArrayList`, `LinkedList`)의 차이, Generic의 필요성과 타입 소거,
Java Collection Framework(List/Set/Map)의 기본 구조를 정리.

## 목차

1. [List와 배열](#1-list와-배열)
2. [ArrayList vs LinkedList](#2-arraylist-vs-linkedlist)
3. [Generic](#3-generic)
4. [Java Collection Framework](#4-java-collection-framework)
5. [Set — HashSet vs TreeSet](#5-set--hashset-vs-treeset)

---

## 1. List와 배열

**Q. List와 배열의 가장 본질적인 차이는 무엇인가요?**

배열은 생성 시점에 크기가 고정되고, 요소 추가·삭제·포함 여부 확인 같은 동작을 언어 차원의 기능이
아니라 개발자가 직접 반복문으로 구현해야 한다. `List`는 이런 배열의 한계를 보완하기 위해 자바가
제공하는 인터페이스로, **크기가 가변적**이고(요소를 추가하면 내부적으로 알아서 용량을 늘림),
`add()`/`remove()`/`contains()`/`size()`처럼 배열이 기본 지원하지 않는 다양한 기능을 메서드로
제공한다. 즉 "고정 크기 + 기능 없음"인 배열을, "가변 크기 + 풍부한 기능"으로 감싼 것이 `List`의
본질이다. 지금까지 배열을 써 왔다면 특별한 이유가 없는 한 배열 대신 `List`를 쓰는 것이 원칙이다.

`List`의 대표 구현체는 `ArrayList`와 `LinkedList` 두 가지이며, 일반적으로 `ArrayList`를 가장 많이
사용한다.

---

## 2. ArrayList vs LinkedList

**Q. ArrayList와 LinkedList는 사용법(메서드)이 같은데, 내부 구조는 어떻게 다른가요?**

두 클래스 모두 `List` 인터페이스를 구현하므로 `add()`, `get()`, `remove()` 같은 사용법은 동일하다.
다른 점은 **데이터를 어떻게 저장하느냐**뿐이다.

**ArrayList — 배열 기반**

내부적으로 `Object[]` 배열을 가지고 있고, 모든 요소가 메모리상에 **연속적으로** 배치된다.

```
index:  [0]    [1]    [2]    [3]
        [A]    [B]    [C]    [D]
         ↑
         연속된 메모리 공간 (배열 기반)
```

인덱스로 접근하면 배열의 오프셋 계산만으로 바로 위치를 찾을 수 있어 `get(i)`가 O(1)이다. 반면 중간에
요소를 삽입·삭제하면 그 뒤의 요소들을 전부 한 칸씩 밀거나 당겨야 해서 O(n)이 걸리고, 용량이 꽉 차면
더 큰 배열을 새로 만들어 복사하는 비용도 든다.

**LinkedList — 노드 기반**

내부적으로 `Node` 객체가 `data` + `prev` + `next` 참조를 가진다(자바는 doubly linked list). 각 노드는
메모리상 **불연속적으로** 흩어져 있다.

```
[A | next] → [B | next] → [C | next] → [D | null]
  ↑              ↑              ↑
  각 노드가 힙 메모리 여기저기에 흩어져 있음
  (노드 = 데이터 + 다음 노드 주소)
```

3번째 요소에 접근하려면 head부터 `next`를 3번 따라가야 하므로 `get(i)`가 O(n)이다. 대신 이미 위치를
찾은 노드 앞뒤에 요소를 끼워 넣거나 빼는 것은 포인터만 바꾸면 되므로 O(1)이다(단, "그 위치를 찾아가는
비용"은 여전히 O(n)이라, 실제 체감 성능은 순수 삽입 비용만큼 좋아지지 않는 경우가 많다).

**어느 걸 써야 하는가**: 어느 클래스를 사용할지 잘 모르겠다면 `ArrayList`를 사용한다. 인덱스 접근이
빠르고, 메모리가 연속적이라 캐시 지역성(cache locality)도 좋아 대부분의 상황에서 더 유리하다.
`LinkedList`는 양쪽 끝에서의 삽입/삭제가 잦은 큐/덱(Deque) 용도로 쓰일 때 이점이 있다.

---

## 3. Generic

### Generic을 사용하지 않을 경우

Generic이 없던 시절에는 컬렉션이 모든 요소를 `Object`로 다뤘다. 다양한 종류의 객체를 담을 수는
있지만(특정 타입 → `Object`), 꺼내 쓰려면 다시 원래 타입으로 **형변환**을 해야 한다(`Object` → 특정
타입).

```java
ArrayList list = new ArrayList();
list.add("this is string");
list.add(1);
list.add(new Position(1, 2));

String first = (String) list.get(0);
int second = (int) list.get(1);
Position third = (Position) list.get(2);
```

이 방식의 문제는 **컴파일러가 타입을 전혀 검증해주지 않는다**는 것이다. `list.get(0)`을 실수로
`(int)`로 캐스팅해도 컴파일은 되고, 실행 시점에서야 `ClassCastException`이 터진다.

### Generic을 사용하는 경우

`ArrayList<String>`처럼 `<>`를 사용해 `ArrayList`가 관리하는 타입을 지정한다. 타입을 지정하면 해당
타입만 추가할 수 있고, 다른 타입을 넣으면 **컴파일 에러**가 발생한다.

```java
ArrayList<String> values = new ArrayList<String>();
values.add("first");
values.add("second");

String first = values.get(0);   // 캐스팅 불필요
String second = values.get(1);
```

**Q. Generic을 사용하지 않아도 코드가 실행되는데, 왜 사용하나요?**

맞다, Generic 없이도 `Object` 기반으로 똑같이 동작하게 짤 수는 있다. 하지만 Generic을 쓰는 이유는
두 가지다.

1. **타입 안정성**: 특정 타입으로 제한함으로써, 잘못된 타입이 섞여 들어가는 실수를 **런타임이 아니라
   컴파일 타임에** 잡아낸다. `ClassCastException`처럼 배포 후에야 터지는 버그를 미리 막을 수 있다.
2. **코드 간결성**: 꺼낼 때마다 하던 타입 체크·형변환을 생략할 수 있어 코드가 짧고 읽기 쉬워진다.

특별히 예외적인 상황이 아니라면 Generic을 사용하는 것이 원칙이다.

**Q. ArrayList\<String\>에는 왜 String만 들어갈 수 있나요?**

`ArrayList<String>`으로 선언하는 순간, 컴파일러가 그 인스턴스에 대한 `add()`의 메서드 시그니처를
`add(String)`으로 취급한다. 그래서 `String`이 아닌 값을 넣으려는 코드는 컴파일 단계에서 타입이
안 맞다고 걸러진다 — 즉 "String만 들어갈 수 있다"는 규칙은 **컴파일러가 강제하는 정적 검사**이지,
런타임에 `ArrayList` 객체 스스로 타입을 검사해서 막는 게 아니다(이어지는 질문 참고).

**Q. 런타임 관점에서 봤을 때 List\<Object\>와 List\<String\>은 서로 다른 타입인가요?**

아니다, **같은 타입이다.** 이걸 이해하려면 자바 Generic의 **타입 소거(Type Erasure)**를 알아야 한다.
자바의 제네릭 타입 정보(`<String>`, `<Object>` 같은 타입 파라미터)는 **컴파일 타임에만** 존재하고,
컴파일된 바이트코드로 변환되는 과정에서 전부 지워진다. 즉 컴파일된 클래스 파일 안에서
`List<String>`과 `List<Object>`는 둘 다 그냥 `List`(raw type)일 뿐이고, 실제로 `list.getClass()`를
호출하면 둘 다 똑같이 `java.util.ArrayList`를 반환한다. `List<String>.class`라는 문법이 애초에
존재하지 않는 이유도 이것이다 — 런타임에는 구분할 방법 자체가 없기 때문이다.

이렇게 설계된 이유는 **하위 호환성**이다. Generic은 Java 5에서 처음 도입됐는데, 그 이전에 짜여진
Generic 없는 코드(raw type을 쓰는 레거시 코드)와 바이트코드 레벨에서 호환되도록 하기 위해, 컴파일러가
타입 검사만 컴파일 타임에 해주고 실제 실행되는 바이트코드는 예전과 동일하게(타입 정보 없이)
만들어지도록 설계됐다.

---

## 4. Java Collection Framework

**Java Collection Framework(JCF)**란, 자료의 집합 형태에 대한 표준적인 프레임워크를 정의한
인터페이스·유틸리티들의 목록이며 JDK 1.2에서 명명되었다. `java.util` 패키지에 속하며, 자바로 자료구조와
알고리즘을 정의하고 동작시키는 클래스 라이브러리를 의미한다.

대표적인 컬렉션 3종의 특징:

| 컬렉션 | 순서 | 중복 |
|---|---|---|
| `List` | 순서가 있는 데이터의 집합 | 허용 |
| `Set` | 순서를 유지하지 않는 데이터의 집합 | 허용하지 않음 |
| `Map` | key-value 쌍으로 이루어진 데이터의 집합 | 순서 유지 안 됨, key는 중복 허용하지 않음 |

---

## 5. Set — HashSet vs TreeSet

`java.util.Set` 인터페이스는 객체의 **중복을 허용하지 않는다**. 이 중복 여부는 `equals()`와
`hashCode()` 메서드를 이용해 판단하므로, `Set`에 담을 객체는 이 두 메서드를 목적에 맞게 오버라이드
해야 한다. 대표적인 구현체로 `HashSet`과 `TreeSet`(정렬 가능)이 있다.

**공통점**: 둘 다 `Set` 인터페이스를 구현하며, 객체의 중복을 허용하지 않는다.

**차이점**:
- **HashSet**: 데이터의 순서와 상관없이 중복만 허용하지 않는 집합. 내부적으로 해시 테이블을 사용해
  `add`/`contains` 등이 평균 O(1)에 동작한다.
- **TreeSet**: Sorted Collection이다. 오름차순/내림차순으로 정렬 가능하며, 내부적으로 이진 탐색
  트리(Binary Search Tree)를 이용해 구현된다. 원소가 정렬 가능해야 하므로, 담을 객체가
  `Comparable`을 구현하거나 `TreeSet` 생성 시 `Comparator`를 넘겨줘야 한다.

**equals()/hashCode()를 안 지키면 생기는 문제 예시**: 아래처럼 좌표를 표현하는 불변 클래스가 있다고
하자.

```java
public class Point {
    private final int x;
    private final int y;

    private Point(int x, int y) {
        if (x < 0 || x > 24) throw new IllegalArgumentException();
        this.x = x;
        if (y < 0 || y > 24) throw new IllegalArgumentException();
        this.y = y;
    }

    public static Point of(int x, int y) {
        return new Point(x, y);
    }
}
```

이 클래스는 `equals()`/`hashCode()`를 오버라이드하지 않았다. 이 상태에서 `HashSet<Point>`에
`Point.of(1, 1)`을 두 번 넣으면 **중복으로 걸러지지 않고 둘 다 들어간다.** `Object`의 기본
`equals()`는 참조(주소) 비교이기 때문에, 좌표값이 같아도 `new`로 만들어진 서로 다른 인스턴스는 다른
객체로 취급된다. `Set`이 "값이 같으면 중복"으로 동작하게 하려면, `x`와 `y` 필드를 기준으로
`equals()`와 `hashCode()`를 반드시 오버라이드해야 한다.

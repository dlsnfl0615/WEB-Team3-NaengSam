# 소프티어 프로젝트 Java 코드 스타일 가이드

이 문서는 `docs/intelij-style.xml`에 정의된 **SofteerStyle** IntelliJ 코드 스타일 스킴을 설명합니다. 포맷은 IDE가 자동으로 맞춰주므로, 각자 스킴을 import한 뒤 포맷 단축키만 눌러주시면 됩니다. 아래 내용은 "왜 이런 모양으로 정렬되는가"를 이해하기 위한 참고 자료입니다.

## 1. 적용 방법

1. IntelliJ에서 `Settings` > `Editor` > `Code Style` 로 이동합니다.
2. 우측 상단 톱니바퀴 아이콘 > `Import Scheme` > `IntelliJ IDEA code style XML` 을 선택합니다.
3. `docs/intelij-style.xml` 파일을 선택하고, 스킴 이름이 `SofteerStyle`로 표시되는지 확인 후 `Apply` 합니다.
4. Java 파일에서 `Ctrl + Alt + L` (macOS: `Cmd + Opt + L`)로 포맷을 적용합니다.

커밋 전에는 반드시 포맷을 한 번 적용해주세요. 포맷 차이로 인한 불필요한 diff를 줄일 수 있습니다.

## 2. 들여쓰기

| 설정                       | 값    |
| -------------------------- | ----- |
| `INDENT_SIZE`              | 4     |
| `CONTINUATION_INDENT_SIZE` | 8     |
| `TAB_SIZE`                 | 4     |
| `USE_TAB_CHARACTER`        | false |

- 들여쓰기는 **스페이스 4칸**을 사용합니다. 탭 문자는 사용하지 않습니다.
- 한 줄이 길어져서 다음 줄로 이어지는 경우(continuation)에는 **8칸**을 들여씁니다.

```java
public class Recipe {

    private void cook() {
        String result = repository.findByName(name)
                .orElseThrow(() -> new IllegalArgumentException("not found"));
    }
}
```

## 3. 줄 길이

| 설정           | 값  |
| -------------- | --- |
| `RIGHT_MARGIN` | 120 |

- 한 줄은 **120자**를 기준으로 합니다. 이를 넘으면 줄바꿈이 발생합니다.
- 에디터 우측에 세로 가이드 라인이 120자 위치에 표시됩니다.

## 4. import

| 설정                                  | 값   |
| ------------------------------------- | ---- |
| `INSERT_INNER_CLASS_IMPORTS`          | true |
| `CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND` | 999  |
| `NAMES_COUNT_TO_USE_IMPORT_ON_DEMAND` | 999  |

- **와일드카드 import(`import java.util.*;`)를 사용하지 않습니다.** 같은 패키지에서 999개를 import해야 압축되므로 사실상 발생하지 않습니다.
- 내부 클래스(inner class)도 직접 import 합니다.
- import 배치 순서는 `IMPORT_LAYOUT_TABLE`에 따라 **static import → 빈 줄 → 일반 import** 입니다.

```java
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
```

## 5. 줄바꿈 규칙

아래 항목들은 모두 `WRAP` 값이 `1`, 즉 **"필요할 때만 줄바꿈(Wrap if long)"** 으로 설정되어 있습니다. 짧으면 한 줄로 두고, 120자를 넘으면 자동으로 나눕니다.

- 메서드 호출 인자 (`CALL_PARAMETERS_WRAP`)
- 메서드 선언 파라미터 (`METHOD_PARAMETERS_WRAP`)
- `extends` / `implements` 목록 (`EXTENDS_LIST_WRAP`)
- `throws` 절 (`THROWS_KEYWORD_WRAP`)
- 메서드 체이닝 (`METHOD_CALL_CHAIN_WRAP`)
- 이항 연산 (`BINARY_OPERATION_WRAP`)
- 삼항 연산 (`TERNARY_OPERATION_WRAP`)
- `for` 문 (`FOR_STATEMENT_WRAP`)
- 배열 초기화 (`ARRAY_INITIALIZER_WRAP`)

또한 `BINARY_OPERATION_SIGN_ON_NEXT_LINE`, `TERNARY_OPERATION_SIGNS_ON_NEXT_LINE`가 `true`이므로, **연산자는 앞 줄 끝이 아니라 다음 줄 맨 앞**에 위치합니다.

```java
// 이항 연산 - 연산자가 다음 줄 앞에 옵니다
boolean valid = ingredient.isFresh()
        && ingredient.getQuantity() > 0
        && !ingredient.isExpired();

// 삼항 연산 - ? 와 : 가 다음 줄 앞에 옵니다
String label = count > 0
        ? "재고 있음"
        : "재고 없음";

// 메서드 체이닝
List<String> names = ingredients.stream()
        .filter(Ingredient::isFresh)
        .map(Ingredient::getName)
        .toList();
```

## 6. 중괄호

| 설정                                                                               | 값         |
| ---------------------------------------------------------------------------------- | ---------- |
| `IF_BRACE_FORCE` / `WHILE_BRACE_FORCE` / `DOWHILE_BRACE_FORCE` / `FOR_BRACE_FORCE` | 3 (Always) |
| `KEEP_CONTROL_STATEMENT_IN_ONE_LINE`                                               | false      |

- 본문이 한 줄이더라도 **제어문에는 항상 중괄호를 붙입니다.**
- 제어문을 한 줄로 붙여 쓰는 것도 허용하지 않습니다. 포맷 시 자동으로 줄이 나뉩니다.

```java
// Before
if (ingredient == null) return;

// After
if (ingredient == null) {
    return;
}
```

## 7. 빈 줄

| 설정                             | 값  |
| -------------------------------- | --- |
| `KEEP_BLANK_LINES_IN_CODE`       | 1   |
| `BLANK_LINES_AFTER_CLASS_HEADER` | 0   |
| `KEEP_BLANK_LINES_BEFORE_RBRACE` | 0   |

- 코드 중간의 연속된 빈 줄은 **최대 1줄**까지만 유지됩니다.
- 클래스 선언 직후에는 빈 줄을 넣지 않습니다.
- 닫는 중괄호 `}` 바로 앞의 빈 줄은 제거됩니다.

```java
public class IngredientService {
    private final IngredientRepository repository;

    public IngredientService(IngredientRepository repository) {
        this.repository = repository;
    }
}
```

## 8. Javadoc / 주석

| 설정                                                                           | 값    |
| ------------------------------------------------------------------------------ | ----- |
| `JD_ALIGN_PARAM_COMMENTS`                                                      | false |
| `JD_ALIGN_EXCEPTION_COMMENTS`                                                  | false |
| `JD_P_AT_EMPTY_LINES`                                                          | false |
| `JD_KEEP_EMPTY_PARAMETER` / `JD_KEEP_EMPTY_RETURN` / `JD_KEEP_EMPTY_EXCEPTION` | false |
| `WRAP_COMMENTS`                                                                | true  |

- `@param`, `@throws`의 설명을 세로로 정렬하지 않습니다.
- 빈 줄에 `<p>` 태그를 자동으로 넣지 않습니다.
- **설명이 비어 있는 `@param`, `@return`, `@throws` 태그는 포맷 시 제거됩니다.** 태그를 남기려면 설명을 함께 작성해주세요.
- 주석도 120자를 넘으면 줄바꿈됩니다.

```java
/**
 * 재료를 저장합니다.
 *
 * @param ingredient 저장할 재료
 * @return 저장된 재료의 식별자
 */
public Long save(Ingredient ingredient) {
    ...
}
```

## 9. 기타

| 설정                                    | 값   |
| --------------------------------------- | ---- |
| `SPACE_BEFORE_ARRAY_INITIALIZER_LBRACE` | true |

- 배열 초기화 시 여는 중괄호 앞에 공백을 둡니다.

```java
int[] counts = {1, 2, 3};
```

### Reference

<https://soeun2537.tistory.com/67>

# CLAUDE.md — 백엔드 작업 지침

이 파일은 Claude Code가 `backend/`에서 작업할 때 따르는 규칙입니다.

- 커밋 / PR / 브랜치 규칙의 **단일 진실 소스(SSOT)**: [../docs/git-convention.md](../docs/git-convention.md)
- 코드 포맷(들여쓰기·줄바꿈·import 등) SSOT: [../docs/java-convention.md](../docs/java-convention.md) — IntelliJ `SofteerStyle` 스킴이 자동으로 맞춰줍니다.

이 문서는 위 두 문서를 **중복 서술하지 않고**, 문서에 없는 백엔드 아키텍처 패턴을 처방합니다. 작업 전 세 문서를 함께 참고하세요.

## 핵심 원칙 — 오버엔지니어링 지양

- **YAGNI.** 지금 명세에 없는 확장성·추상화를 미리 만들지 않습니다. 불필요한 인터페이스, 범용 유틸, 미래 대비 옵션 파라미터, 과도한 제네릭·디자인 패턴을 넣지 마세요.
- 요구된 기능을 **가장 단순하고 읽기 쉬운 방법**으로 구현합니다. 기존 패턴·유틸을 먼저 재사용하고, 새 추상화는 실제 중복이 **2회 이상** 생겼을 때 도입합니다.
- 새 라이브러리·설정 계층·전역 구조 변경은 필요성이 분명할 때만. 애매하면 팀에 먼저 물어보세요.

## 스택 & 명령

- **Java 21** + **Spring Boot** + **JPA/Hibernate**
- DB: 로컬 **H2**, 운영은 환경변수(`DATABASE_URL` 등) 주입 — `spring.jpa.hibernate.ddl-auto=none` (스키마는 SQL 스크립트로 관리)
- API 문서: **SpringDoc OpenAPI (Swagger)** / SMS: **Solapi** / 보일러플레이트: **Lombok**

```bash
./gradlew build        # 컴파일 + 테스트
./gradlew test         # 테스트만
./gradlew bootRun      # 로컬 실행
```

## 패키지 구조

`com.naengsam.quick` 아래 **도메인 계층 + 공통(global) 계층**으로 나눕니다. 도메인끼리는 독립적으로 유지합니다(에러코드·예외도 도메인별로 분리).

```
com.naengsam.quick/
├── domain/<name>/            # 비즈니스 도메인 (user, boormi, dreami, address, order, ...)
│   ├── controller/
│   ├── service/
│   ├── repository/
│   ├── entity/               # 엔티티 + <Domain>Cd enum
│   ├── dto/                  # <name>Request / <name>Dto (record)
│   └── exception/            # <Domain>ErrorCode enum
└── global/                   # 크로스커팅 인프라
    ├── commonResponse/       # CommonResponse, CommonResponseAdvice
    ├── code/                 # BaseCode, BaseErrorCode, GeneralErrorCode, GeneralSuccessCode
    ├── exception/            # BusinessException, GlobalExceptionHandler
    ├── session/             # LoginSession, LoginUser, LoginRequired
    ├── swagger/              # ApiErrorCodes, 커스터마이저
    └── config/
```

## 응답 처리 (필수)

- 컨트롤러는 **DTO를 그대로 반환하거나 `void`** 로 둡니다. `CommonResponseAdvice`가 성공 envelope로 **자동 래핑**합니다.
- **`CommonResponse<Void>`를 직접 만들거나 `CommonResponse.onSuccess(...)`를 컨트롤러에서 호출하지 마세요.** 반환 타입은 순수 DTO/void 입니다.
- 응답 JSON 형태는 항상 다음과 같습니다:

```json
{ "isSuccess": true, "code": "COM200", "message": "...", "result": { ... } }
```

## 에러 처리 (필수)

- 비즈니스 예외는 **`throw new BusinessException(도메인ErrorCode.XXX)`** 로 던집니다.
- 에러코드는 **도메인별 enum**(`AuthErrorCode`, `UserErrorCode` 등)이 `BaseErrorCode`를 구현하고, 공통은 `GeneralErrorCode`를 씁니다. 항목 형식은 `(HttpStatus, "CODE_NNN", "한글 메시지")` 입니다.

```java
UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "AUTH_001", "로그인이 필요합니다."),
```

- `GlobalExceptionHandler`가 전역에서 처리하므로 **컨트롤러/서비스에서 try-catch로 응답을 조립하거나 `ResponseEntity`를 직접 만들지 않습니다.** 예외를 던지기만 하세요.

## 컨트롤러 규칙

- `@RestController` + `@RequestMapping("/api/v1/<domain>")` + `@RequiredArgsConstructor` + `@Slf4j`.
- 요청 바디는 `@Valid @RequestBody <name>Request`.
- Swagger 문서화: 클래스에 `@Tag`, 메서드에 `@Operation` / `@ApiResponse`, 발생 가능한 에러는 **반복 가능한** `@ApiErrorCodes(enumClass = XxxErrorCode.class, codes = {"..."})`.
- 인증이 필요한 엔드포인트는 `@LoginRequired` + 파라미터로 `@LoginUser UUID boormiId`. 세션 생성/무효화는 `LoginSession` 사용.

```java
@Operation(summary = "내 정보", description = "로그인한 사용자 정보를 반환한다.")
@GetMapping("/me")
@ApiErrorCodes(enumClass = AuthErrorCode.class, codes = {"INVALID_SESSION"})
public UserDto me(@LoginUser UUID boormiId) {
    return userService.getUserInfo(boormiId);
}
```

## 서비스 규칙

- `@Service` + `@RequiredArgsConstructor`. 의존성은 생성자 주입(`private final`).
- 상태를 바꾸는 메서드는 `@Transactional`, 조회 전용은 `@Transactional(readOnly = true)`.

## 엔티티 규칙

- `@Entity` + `@Getter` + `@NoArgsConstructor(access = AccessLevel.PROTECTED)`.
- 생성은 **정적 팩토리 `create(...)`** 로만 합니다. 외부에서 `new` / setter로 만들지 않습니다.
- PK는 `UUID` + `@JdbcTypeCode(SqlTypes.BINARY)` + `columnDefinition = "BINARY(16)"`.
- 상태 값은 `@Enumerated(EnumType.STRING)` + `<Domain>Cd` enum.
- `BaseEntity`는 두지 않습니다(각 엔티티가 필드를 독립 관리).

```java
@Entity
@Table(name = "BOORMI")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Boormi {
    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "boormi_id", columnDefinition = "BINARY(16)")
    private UUID boormiId;
    // ...
    public static Boormi create(String email, String password, String name,
            String phoneNumber, LocalDate birthdate) {
        Boormi boormi = new Boormi();
        boormi.boormiId = UUID.randomUUID();
        // ...
        return boormi;
    }
}
```

## DTO 규칙

- **record 우선.** 요청 DTO는 `<name>Request`, 응답 DTO는 `<name>Dto`.
- 요청 DTO에는 Jakarta Validation 어노테이션(`@NotBlank`, `@Email`, `@Size`, `@Pattern`, `@Past` 등)을 필드에 직접 부착합니다.
- 엔티티 → 응답 DTO 변환은 정적 팩토리 `from(...)`. getter는 record 접근자(`email()`) 형태입니다.

```java
public record UserDto(UUID boormiId, String email, String name, boolean isDreami) {
    public static UserDto from(Boormi boormi, boolean isDreami) {
        return new UserDto(boormi.getBoormiId(), boormi.getEmail(), boormi.getName(), isDreami);
    }
}
```

## 테스트 규칙 (필수)

- 스택: **JUnit 5 + Mockito(BDD) + AssertJ**. 서비스 단위 테스트는 `@ExtendWith(MockitoExtension.class)` + `@Mock`/`@InjectMocks`, 의존성(Repository·다른 서비스)은 목킹합니다.
- **BDD 스타일**: `given(...).willReturn(...)` → 실행 → `assertThat(...)` / `verify(...)`. given·when·then은 빈 줄로 구분합니다.
- **테스트 메서드명은 한글** `상황_기대결과` 서술형으로 씁니다. 클래스에는 목적을 한 줄 Javadoc으로 답니다.
- **픽스처는 정적 팩토리 헬퍼**(`signUpRequest()`, `activeBoormi()`)로 만들고, 엔티티 상태 강제는 `ReflectionTestUtils.setField(...)`를 씁니다(setter가 없으므로).
- **비즈니스 예외 검증**: `catchThrowable(...)`로 잡아 `((BusinessException) t).getErrorCode()`가 기대 enum과 같은지 `assertThat`. "저장 안 함"은 `verify(repo, never()).save(any())`.
- 클래스명은 단위 `<Class>Test`, 통합 `<Class>IntegrationTest`. `assertThat`/`given` 등은 static import 합니다.
- **새 서비스 로직·버그 수정에는 테스트를 동반**합니다. 검증 분기와 상태 전이를 커버하세요.

```java
@Test
void 가입_이메일이_중복이면_ALREADY_REGISTERED_예외() {
    given(boormiRepository.existsByEmail("user@test.com")).willReturn(true);

    Throwable thrown = catchThrowable(() -> userService.signup(signUpRequest()));

    assertThat(errorCodeOf(thrown)).isEqualTo(AuthErrorCode.ALREADY_REGISTERED);
    verify(boormiRepository, never()).save(any());
}
```

## 네이밍 요약

| 구성 요소 | 규칙 | 예시 |
| --- | --- | --- |
| Controller | `<Domain>Controller` | `UserController` |
| Service | `<Domain>Service` | `UserService` |
| Repository | `<Domain>Repository` (JpaRepository 상속) | `BoormiRepository` |
| ErrorCode | `<Domain>ErrorCode` (BaseErrorCode 구현 enum) | `AuthErrorCode` |
| 요청 DTO | `<name>Request` (record) | `SignUpRequest` |
| 응답 DTO | `<name>Dto` (record) | `UserDto` |
| Entity | 도메인 PascalCase | `Boormi`, `Address` |
| 상태 enum | `<Domain>Cd` | `UserCd`, `DreamiCd` |
| API 경로 | `/api/v1/<domain>/<resource>` | `/api/v1/user/signup` |

## 커밋 & PR

상세는 [../docs/git-convention.md](../docs/git-convention.md)가 SSOT입니다. 요약:

- **커밋**: `BE/<Type> : <한글 헤더>` (Type은 대문자 `Feat`/`Fix`/`Refactor`/`Test`/`Docs`/`Etc`).
  예: `BE/Feat : 회원가입 서비스 구현`
- **브랜치**: `feat/<issue번호>` (예: `feat/37`). develop에서 분기하고, **main에 직접 push 금지**.
- **PR**: develop 대상으로 생성하며 `.github/PULL_REQUEST_TEMPLATE.md` 형식(변경 요약 → 관련 이슈 → 변경 내용 → 테스트 체크리스트)을 채웁니다. 최소 2명 리뷰 후 merge.

## 검증

작업을 마치면 항상 다음을 통과시키세요. 커밋 전 IntelliJ 포맷([../docs/java-convention.md](../docs/java-convention.md))을 한 번 적용합니다.

```bash
./gradlew build   # 컴파일 + 테스트 통과 확인
```

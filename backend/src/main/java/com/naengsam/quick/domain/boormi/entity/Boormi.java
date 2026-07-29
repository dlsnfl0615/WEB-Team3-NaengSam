package com.naengsam.quick.domain.boormi.entity;

import com.naengsam.quick.domain.user.entity.UserCd;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

// **
// * 부르미(일반 사용자) 엔티티. 스키마는 {@code sql/sym-boorm-ddl.sql} 로 생성되며 ({@code spring.jpa.hibernate.ddl-auto=none}) 여기서는 기존
// * BOORMI 테이블에 매핑만 한다. 로그인에 필요한 컬럼만 매핑한다.
// */
@Entity
@Table(name = "BOORMI")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Boormi {

    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "boormi_id", columnDefinition = "BINARY(16)")
    private UUID boormiId;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "password", nullable = false)
    private String password;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "phone_number", nullable = false)
    private String phoneNumber;

    @Column(name = "birthdate", nullable = false)
    private LocalDate birthdate;

    @Enumerated(EnumType.STRING)
    @Column(name = "user_cd")
    private UserCd userCd;

    @Column(name = "is_dreami_activated", nullable = false)
    private boolean isDreamiActivate;

    /**
     * 신규 가입 부르미를 생성한다. PK 는 앱에서 생성(BINARY(16), DB 기본값 없음)하며 상태는 ACTIVE 로 시작한다.
     * {@code boormi_avg_score}/{@code created_dtm} 은 미매핑이므로 INSERT 에서 제외되어 DB 기본값이 적용된다.
     */
    public static Boormi create(String email, String password, String name, String phoneNumber,
            LocalDate birthdate) {
        Boormi boormi = new Boormi();
        boormi.boormiId = UUID.randomUUID();
        boormi.email = email;
        boormi.password = password;
        boormi.name = name;
        boormi.phoneNumber = phoneNumber;
        boormi.birthdate = birthdate;
        boormi.userCd = UserCd.ACTIVE;
        boormi.isDreamiActivate = false;
        return boormi;
    }
}

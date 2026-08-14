package com.naengsam.quick.domain.user.dto;

import com.naengsam.quick.domain.user.entity.UserCd;
import java.util.UUID;

/**
 * 로그인 검증에 필요한 값만 담아 트랜잭션 밖으로 들고 나가는 detached record.
 *
 * <p>PBKDF2 해싱은 트랜잭션 밖에서 수행하는데, 그때 {@code Boormi} 엔티티를 그대로 들고 나가면
 * 준영속 엔티티에 접근하게 되고 지연 로딩이 터질 수 있다. 필요한 세 값만 복사해서 나간다.
 */
public record LoginCredential(UUID boormiId, String passwordHash, UserCd userCd) {
}

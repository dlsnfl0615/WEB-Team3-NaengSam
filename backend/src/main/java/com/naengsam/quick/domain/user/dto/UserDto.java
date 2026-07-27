package com.naengsam.quick.domain.user.dto;

import com.naengsam.quick.domain.boormi.entity.Boormi;
import java.util.UUID;

public record UserDto(
        UUID boormiId,
        String email,
        String name,
        boolean isDreami
) {
    public static UserDto from(Boormi boormi, boolean isDreami) {
        return new UserDto(boormi.getBoormiId(), boormi.getEmail(), boormi.getName(), isDreami);
    }
}

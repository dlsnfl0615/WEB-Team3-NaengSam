package com.naengsam.quick.domain.user.dto;

import com.naengsam.quick.domain.boormi.entity.Boormi;
import java.util.UUID;

public record UserDto(
        UUID boormiId,
        String email,
        String name,
        boolean isDreami,
        ActiveRole activeRole,
        UUID activeOrderId
) {
    public static UserDto from(Boormi boormi, boolean isDreami) {
        return from(boormi, isDreami, null, null);
    }

    public static UserDto from(Boormi boormi, boolean isDreami, ActiveRole activeRole, UUID activeOrderId) {
        return new UserDto(boormi.getBoormiId(), boormi.getEmail(), boormi.getName(), isDreami, activeRole,
                activeOrderId);
    }
}

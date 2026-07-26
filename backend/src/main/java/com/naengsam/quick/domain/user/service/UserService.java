package com.naengsam.quick.domain.user.service;

import com.naengsam.quick.domain.user.dto.UserDto;
import com.naengsam.quick.domain.user.exception.UserErrorCode;
import com.naengsam.quick.global.exception.BusinessException;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    public UserDto hello() {
        return new UserDto("hello");
    }

    public void Error() {
        throw new BusinessException(UserErrorCode.INCORRECT_PASSWORD);
    }
}

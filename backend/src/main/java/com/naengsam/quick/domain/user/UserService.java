package com.naengsam.quick.domain.user;

import org.springframework.stereotype.Service;

@Service
public class UserService {

    public UserDto hello() {
        return new UserDto("hello");
    }
}

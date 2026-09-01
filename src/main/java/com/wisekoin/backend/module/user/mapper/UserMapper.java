package com.wisekoin.backend.module.user.mapper;

import com.wisekoin.backend.module.user.User;
import com.wisekoin.backend.module.user.dto.UserRegisterRequest;
import com.wisekoin.backend.module.user.dto.UserResponse;

public class UserMapper {

    public User toEntity(UserRegisterRequest request) {
        User user = new User();

        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setPassword(request.getPassword());

        return user;
    }

    public UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getEmail(), user.getPhone());
    }

}

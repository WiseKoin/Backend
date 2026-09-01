package com.wisekoin.backend.module.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserRegisterRequest {

    @NotBlank
    private String username;

    @Email
    @NotBlank
    private String email;

    private String phone;

    @NotBlank
    private String password;

}

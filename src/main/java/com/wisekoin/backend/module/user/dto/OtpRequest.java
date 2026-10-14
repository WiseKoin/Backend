package com.wisekoin.backend.module.user.dto;

import com.wisekoin.backend.module.user.otp.OtpPurpose;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OtpRequest {
    @NotNull
    private OtpPurpose purpose;
}

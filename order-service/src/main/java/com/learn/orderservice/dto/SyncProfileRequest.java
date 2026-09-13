package com.learn.orderservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SyncProfileRequest {

    @NotBlank(message = "email is required")
    @Email(message = "email must be a valid email address")
    private String email;
}

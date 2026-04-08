package com.toggle.dto.auth;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.toggle.global.config.TrimmedStringDeserializer;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
    @JsonDeserialize(using = TrimmedStringDeserializer.class)
    @NotBlank
    @Email
    String email,

    @NotBlank
    String password
) {
}

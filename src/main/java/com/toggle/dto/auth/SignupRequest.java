package com.toggle.dto.auth;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.toggle.entity.UserRole;
import com.toggle.global.config.TrimmedStringDeserializer;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignupRequest(
    @JsonDeserialize(using = TrimmedStringDeserializer.class)
    @NotBlank
    @Email
    String email,

    @NotBlank
    @Size(min = 8, max = 100)
    String password,

    @JsonDeserialize(using = TrimmedStringDeserializer.class)
    String nickname,

    @JsonDeserialize(using = TrimmedStringDeserializer.class)
    String ownerDisplayName,

    UserRole role
) {
}

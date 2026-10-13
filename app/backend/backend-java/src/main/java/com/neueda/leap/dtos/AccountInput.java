package com.neueda.leap.dtos;

import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.media.Schema;

public record AccountInput(

    @Schema(description = "Login username (must be unique)", example = "frank")
    @NotBlank(message = "Username must not be blank.")
    String username,

    @Schema(description = "Password (will be hashed before storage)", example = "s3cur3P@ss!")
    @NotBlank(message = "Password must not be blank.")
    String password,

    @Schema(description = "Email address", example = "frank.smith@email.com")
    String email,

    @Schema(description = "Phone number", example = "+44-1234-567895")
    String phone,

    @Schema(description = "Comma-separated account roles", example = "USER")
    String roles
) {
    public AccountInput(String username, String password, String email, String phone) {
        this(username, password, email, phone, null);
    }
}

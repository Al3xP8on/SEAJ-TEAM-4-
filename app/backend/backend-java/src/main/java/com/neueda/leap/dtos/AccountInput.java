// Defines the account registration request with required username, password and valid email.
// Retains the legacy phone field while email verification is handled by the authentication service.
package com.neueda.leap.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Email;
import io.swagger.v3.oas.annotations.media.Schema;

public record AccountInput(

    @Schema(description = "Login username (must be unique)", example = "frank")
    @NotBlank(message = "Username must not be blank.")
    String username,

    @Schema(description = "Password (will be hashed before storage)", example = "s3cur3P@ss!")
    @NotBlank(message = "Password must not be blank.")
    String password,

    @NotBlank @Email
    @Schema(description = "Email address for registration and login verification", example = "frank.smith@email.com")
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

package com.neueda.leap.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

public record AccountInput(

    @Schema(description = "Public account identifier (must be unique)", example = "ACC-0006")
    @NotBlank(message = "Account ID must not be blank.")
    String accountId,

    @Schema(description = "Login username (must be unique)", example = "frank")
    @NotBlank(message = "Username must not be blank.")
    String username,

    @Schema(description = "Password (will be hashed before storage)", example = "s3cur3P@ss!")
    @NotBlank(message = "Password must not be blank.")
    String password,

    @Schema(description = "Full name of the account holder", example = "Frank Smith")
    @NotBlank(message = "Name must not be blank.")
    String name,

    @Schema(description = "Email address", example = "frank.smith@email.com")
    String email,

    @Schema(description = "Phone number", example = "+44-1234-567895")
    String phone,

    @Schema(description = "Initial cash balance (optional, defaults to 0)", example = "5000.00")
    @PositiveOrZero(message = "Cash balance must be zero or positive.")
    BigDecimal cashBalance
) {}

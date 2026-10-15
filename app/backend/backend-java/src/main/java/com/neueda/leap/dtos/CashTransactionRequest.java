package com.neueda.leap.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record CashTransactionRequest(
    @Schema(description = "Amount of cash to deposit or withdraw", example = "250.00")
    @NotNull(message = "Amount is required.")
    @Positive(message = "Amount must be positive.")
    @Digits(integer = 13, fraction = 2, message = "Amount can have at most 2 decimal places.")
    @DecimalMax(value = "1000000.00", message = "Amount cannot exceed 1,000,000.00 per transaction.")
    BigDecimal amount
) {}

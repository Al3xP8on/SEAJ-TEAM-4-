// Defines the email verification request forwarded by registration and login endpoints.
// Requires a valid challenge UUID and a six-digit verification code.
package com.neueda.leap.dtos;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
public record EmailVerificationRequest(
    @NotBlank @Pattern(regexp = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-4[0-9a-fA-F]{3}-[89aAbB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}") String challenge_id,
    @NotBlank @Pattern(regexp = "[0-9]{6}") String code
) {}

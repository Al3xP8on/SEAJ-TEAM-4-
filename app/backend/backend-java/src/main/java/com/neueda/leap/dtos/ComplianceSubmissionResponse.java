package com.neueda.leap.dtos;

import com.neueda.leap.compliance.ComplianceDecision;
import com.neueda.leap.compliance.ComplianceOutcome;
import com.neueda.leap.enums.OrderSide;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Schema(description = "One order submission's compliance checks, for the admin audit trail")
public record ComplianceSubmissionResponse(
        UUID checkGroupId,
        LocalDateTime checkedAt,
        String accountId,
        String symbol,
        OrderSide side,
        long quantity,
        BigDecimal price,
        @Schema(description = "BLOCKED, NEEDS_ACKNOWLEDGEMENT or ALLOWED") ComplianceDecision decision,
        @Schema(description = "The order it created, or null when it was stopped") UUID orderId,
        List<Result> results
) {
    @Schema(description = "One rule's result")
    public record Result(String rule, ComplianceOutcome outcome, String reason, boolean acknowledged) { }
}

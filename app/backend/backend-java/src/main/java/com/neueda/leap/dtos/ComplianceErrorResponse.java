package com.neueda.leap.dtos;

import com.neueda.leap.compliance.RuleResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "An order stopped by pre-trade compliance, with every reason")
public record ComplianceErrorResponse(
        @Schema(example = "422") int status,
        @Schema(description = "COMPLIANCE_BLOCKED, or COMPLIANCE_WARNING when warnings need acknowledging",
                example = "COMPLIANCE_BLOCKED") String code,
        @Schema(example = "You can't buy TSLA. It's on the restricted list: under internal review") String message,
        List<Reason> reasons,
        LocalDateTime timestamp,
        String path
) {
    @Schema(description = "One rule's reason for stopping the order")
    public record Reason(
            @Schema(example = "RESTRICTED_LIST") String rule,
            @Schema(description = "BLOCK or WARN", example = "BLOCK") String outcome,
            String message
    ) {
        public static Reason from(RuleResult result) {
            return new Reason(result.ruleId(), result.outcome().name(), result.reason());
        }
    }
}

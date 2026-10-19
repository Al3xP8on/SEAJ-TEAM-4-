package com.neueda.leap.controllers;

import com.neueda.leap.compliance.ComplianceDecision;
import com.neueda.leap.dtos.ComplianceSubmissionResponse;
import com.neueda.leap.dtos.RestrictedInstrumentRequest;
import com.neueda.leap.dtos.RestrictedInstrumentResponse;
import com.neueda.leap.security.SecurityUtilsService;
import com.neueda.leap.services.ComplianceAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Admin-only: the pre-trade compliance audit trail and the restricted list */
@RestController
@RequestMapping("/v1/admin/compliance")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Compliance administration")
public class ComplianceAdminController {
    private final ComplianceAdminService compliance;
    private final SecurityUtilsService security;

    public ComplianceAdminController(ComplianceAdminService compliance, SecurityUtilsService security) {
        this.compliance = compliance;
        this.security = security;
    }

    @Operation(operationId = "listComplianceSubmissions", summary = "Newest order submissions and their compliance checks")
    @GetMapping("/checks")
    public List<ComplianceSubmissionResponse> submissions(
            @RequestParam(required = false) ComplianceDecision decision,
            @RequestParam(required = false) String accountId,
            @RequestParam(required = false) String symbol,
            @RequestParam(defaultValue = "50") int limit) {
        return compliance.submissions(decision, accountId, symbol, limit);
    }

    @Operation(operationId = "listRestrictedInstruments", summary = "Instruments customers can't buy")
    @GetMapping("/restricted")
    public List<RestrictedInstrumentResponse> restricted() {
        return compliance.restrictedList();
    }

    @Operation(operationId = "restrictInstrument", summary = "Add an instrument to the restricted list")
    @PostMapping("/restricted")
    @ResponseStatus(HttpStatus.CREATED)
    public RestrictedInstrumentResponse restrict(@Valid @RequestBody RestrictedInstrumentRequest request) {
        return compliance.restrict(request.symbol(), request.reason(), security.getAuthenticatedUserAccount().getUsername());
    }

    @Operation(operationId = "unrestrictInstrument", summary = "Remove an instrument from the restricted list")
    @DeleteMapping("/restricted/{symbol}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unrestrict(@PathVariable String symbol) {
        compliance.unrestrict(symbol, security.getAuthenticatedUserAccount().getUsername());
    }
}

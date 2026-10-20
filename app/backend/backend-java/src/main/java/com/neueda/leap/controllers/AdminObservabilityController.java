package com.neueda.leap.controllers;

import com.neueda.leap.services.AdminObservabilityService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/admin/observability")
@PreAuthorize("hasRole('ADMIN')")
public class AdminObservabilityController {
    private final AdminObservabilityService monitoring;
    public AdminObservabilityController(AdminObservabilityService monitoring) { this.monitoring = monitoring; }
    @GetMapping
    public AdminObservabilityService.Snapshot snapshot() { return monitoring.snapshot(); }
}

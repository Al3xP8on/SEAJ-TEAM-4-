package com.neueda.leap.controllers;

import com.neueda.leap.compliance.ComplianceReport;
import com.neueda.leap.compliance.RuleResult;
import com.neueda.leap.config.TestSecurityConfig;
import com.neueda.leap.exceptions.ComplianceException;
import com.neueda.leap.messaging.OrderEventPublisher;
import com.neueda.leap.messaging.TradeEventPublisher;
import com.neueda.leap.repositories.AccountRepository;
import com.neueda.leap.repositories.InstrumentsRepository;
import com.neueda.leap.security.SecurityUtilsService;
import com.neueda.leap.services.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** What the API answers when pre-trade compliance stops an order */
@WebMvcTest(OrderController.class)
@Import(TestSecurityConfig.class)
class OrderComplianceControllerTest {
    @Autowired MockMvc mockMvc;
    @MockBean OrderService orderService;
    @MockBean SecurityUtilsService securityUtilsService;
    @MockBean AccountRepository accountRepository;
    @MockBean InstrumentsRepository instrumentsRepository;
    @MockBean OrderEventPublisher orderEventPublisher;
    @MockBean TradeEventPublisher tradeEventPublisher;

    static final String ORDER = "{\"accountId\":\"ACC-0002\",\"symbol\":\"TSLA\",\"side\":\"BUY\",\"quantity\":1,\"price\":200.00}";

    @Test void blockedOrderAnswers422WithTheReason() throws Exception {
        var report = new ComplianceReport(UUID.randomUUID(), List.of(
                RuleResult.block("RESTRICTED_LIST", "You can't buy TSLA. It's on the restricted list: under review", Map.of()),
                RuleResult.pass("CASH_AVAILABLE")));
        when(orderService.placeOrder(any())).thenThrow(ComplianceException.blocked(report));

        mockMvc.perform(post("/v1/orders").contentType(MediaType.APPLICATION_JSON).content(ORDER))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("COMPLIANCE_BLOCKED"))
                .andExpect(jsonPath("$.message").value("You can't buy TSLA. It's on the restricted list: under review"))
                .andExpect(jsonPath("$.reasons", hasSize(1)))
                .andExpect(jsonPath("$.reasons[0].rule").value("RESTRICTED_LIST"))
                .andExpect(jsonPath("$.reasons[0].outcome").value("BLOCK"))
                .andExpect(jsonPath("$.path").value("/v1/orders"));
    }

    @Test void unacknowledgedWarningAnswers422WithEachWarning() throws Exception {
        var warning = RuleResult.warn("SINGLE_STOCK_LIMIT", "After this order, NVDA would be 32% of your portfolio.", Map.of());
        when(orderService.placeOrder(any())).thenThrow(
                ComplianceException.needsAcknowledgement(new ComplianceReport(UUID.randomUUID(), List.of(warning)), List.of(warning)));

        mockMvc.perform(post("/v1/orders").contentType(MediaType.APPLICATION_JSON).content(ORDER))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("COMPLIANCE_WARNING"))
                .andExpect(jsonPath("$.reasons[0].rule").value("SINGLE_STOCK_LIMIT"))
                .andExpect(jsonPath("$.reasons[0].outcome").value("WARN"));
    }

    @Test void acknowledgedWarningsReachTheService() throws Exception {
        when(orderService.placeOrder(any())).thenThrow(new com.neueda.leap.exceptions.OrderException("stop here"));
        String body = ORDER.replace("}", ",\"acknowledgedWarnings\":[\"SINGLE_STOCK_LIMIT\"]}");

        mockMvc.perform(post("/v1/orders").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        verify(orderService).placeOrder(argThat(r -> r.getAcknowledgedWarnings().equals(List.of("SINGLE_STOCK_LIMIT"))));
    }
}

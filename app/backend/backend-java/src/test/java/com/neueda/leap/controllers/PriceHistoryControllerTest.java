package com.neueda.leap.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neueda.leap.models.PriceHistory;
import com.neueda.leap.services.PriceHistoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PriceHistoryController.class)
@DisplayName("PriceHistoryController Tests")
public class PriceHistoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PriceHistoryService priceHistoryService;

    private PriceHistory testPriceHistory;
    private PriceHistory testPriceHistory2;

    @BeforeEach
    void setUp() {
        testPriceHistory = new PriceHistory(
            "AAPL",
            LocalDate.of(2024, 9, 24),
            new BigDecimal("150.00"),
            new BigDecimal("155.00"),
            new BigDecimal("149.50"),
            new BigDecimal("152.00"),
            1000000L
        );

        testPriceHistory2 = new PriceHistory(
            "AAPL",
            LocalDate.of(2024, 9, 25),
            new BigDecimal("152.00"),
            new BigDecimal("158.00"),
            new BigDecimal("151.50"),
            new BigDecimal("156.00"),
            1200000L
        );
    }

    // GET Tests

    @Test
    @DisplayName("Should get all price history for a symbol successfully")
    void testGetPriceHistorySuccess() throws Exception {
        List<PriceHistory> history = Arrays.asList(testPriceHistory, testPriceHistory2);
        when(priceHistoryService.getPriceHistoryBySymbol("AAPL")).thenReturn(history);

        mockMvc.perform(get("/v1/price-history/AAPL")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].symbol", equalTo("AAPL")))
                .andExpect(jsonPath("$[0].close", equalTo(152.00)))
                .andExpect(jsonPath("$[1].close", equalTo(156.00)));

        verify(priceHistoryService, times(1)).getPriceHistoryBySymbol("AAPL");
    }

    @Test
    @DisplayName("Should return empty list when no price history found for symbol")
    void testGetPriceHistoryEmpty() throws Exception {
        when(priceHistoryService.getPriceHistoryBySymbol("UNKNOWN")).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/v1/price-history/UNKNOWN")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        verify(priceHistoryService, times(1)).getPriceHistoryBySymbol("UNKNOWN");
    }

    @Test
    @DisplayName("Should handle error when fetching price history")
    void testGetPriceHistoryError() throws Exception {
        when(priceHistoryService.getPriceHistoryBySymbol("ERROR")).thenThrow(new RuntimeException("Database error"));

        mockMvc.perform(get("/v1/price-history/ERROR")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError());

        verify(priceHistoryService, times(1)).getPriceHistoryBySymbol("ERROR");
    }

    // GET with Date Range Tests

    @Test
    @DisplayName("Should get price history by date range successfully")
    void testGetPriceHistoryByDateRangeSuccess() throws Exception {
        LocalDate fromDate = LocalDate.of(2024, 9, 24);
        LocalDate toDate = LocalDate.of(2024, 9, 25);
        List<PriceHistory> history = Arrays.asList(testPriceHistory, testPriceHistory2);

        when(priceHistoryService.getPriceHistoryBySymbolAndDateRange("AAPL", fromDate, toDate))
            .thenReturn(history);

        mockMvc.perform(get("/v1/price-history/AAPL/range")
                .param("from", "2024-09-24")
                .param("to", "2024-09-25")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].symbol", equalTo("AAPL")))
                .andExpect(jsonPath("$[1].symbol", equalTo("AAPL")));

        verify(priceHistoryService, times(1)).getPriceHistoryBySymbolAndDateRange("AAPL", fromDate, toDate);
    }

    @Test
    @DisplayName("Should return empty list when no price history found in date range")
    void testGetPriceHistoryByDateRangeEmpty() throws Exception {
        LocalDate fromDate = LocalDate.of(2024, 10, 1);
        LocalDate toDate = LocalDate.of(2024, 10, 31);

        when(priceHistoryService.getPriceHistoryBySymbolAndDateRange("AAPL", fromDate, toDate))
            .thenReturn(Collections.emptyList());

        mockMvc.perform(get("/v1/price-history/AAPL/range")
                .param("from", "2024-10-01")
                .param("to", "2024-10-31")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        verify(priceHistoryService, times(1)).getPriceHistoryBySymbolAndDateRange("AAPL", fromDate, toDate);
    }

    @Test
    @DisplayName("Should return 400 when missing date parameters")
    void testGetPriceHistoryByDateRangeMissingParams() throws Exception {
        mockMvc.perform(get("/v1/price-history/AAPL/range")
                .param("from", "2024-09-24")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        verify(priceHistoryService, never()).getPriceHistoryBySymbolAndDateRange(any(), any(), any());
    }

    @Test
    @DisplayName("Should return 400 when invalid date range provided")
    void testGetPriceHistoryByDateRangeInvalidRange() throws Exception {
        LocalDate fromDate = LocalDate.of(2024, 9, 25);
        LocalDate toDate = LocalDate.of(2024, 9, 24);

        when(priceHistoryService.getPriceHistoryBySymbolAndDateRange("AAPL", fromDate, toDate))
            .thenThrow(new IllegalArgumentException("fromDate must be before or equal to toDate"));

        mockMvc.perform(get("/v1/price-history/AAPL/range")
                .param("from", "2024-09-25")
                .param("to", "2024-09-24")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        verify(priceHistoryService, times(1)).getPriceHistoryBySymbolAndDateRange("AAPL", fromDate, toDate);
    }

    // GET Latest Tests

    @Test
    @DisplayName("Should get latest price history for a symbol successfully")
    void testGetLatestPriceHistorySuccess() throws Exception {
        when(priceHistoryService.getLatestPriceHistory("AAPL")).thenReturn(Optional.of(testPriceHistory2));

        mockMvc.perform(get("/v1/price-history/AAPL/latest")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol", equalTo("AAPL")))
                .andExpect(jsonPath("$.close", equalTo(156.00)))
                .andExpect(jsonPath("$.priceDate", equalTo("2024-09-25")));

        verify(priceHistoryService, times(1)).getLatestPriceHistory("AAPL");
    }

    @Test
    @DisplayName("Should return 404 when no latest price history found")
    void testGetLatestPriceHistoryNotFound() throws Exception {
        when(priceHistoryService.getLatestPriceHistory("UNKNOWN")).thenReturn(Optional.empty());

        mockMvc.perform(get("/v1/price-history/UNKNOWN/latest")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());

        verify(priceHistoryService, times(1)).getLatestPriceHistory("UNKNOWN");
    }

    @Test
    @DisplayName("Should handle error when fetching latest price history")
    void testGetLatestPriceHistoryError() throws Exception {
        when(priceHistoryService.getLatestPriceHistory("ERROR"))
            .thenThrow(new RuntimeException("Database error"));

        mockMvc.perform(get("/v1/price-history/ERROR/latest")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError());

        verify(priceHistoryService, times(1)).getLatestPriceHistory("ERROR");
    }

    // POST Tests

    @Test
    @DisplayName("Should save price history successfully")
    void testSavePriceHistorySuccess() throws Exception {
        when(priceHistoryService.savePriceHistory(any(PriceHistory.class))).thenReturn(testPriceHistory);

        String priceHistoryJson = objectMapper.writeValueAsString(testPriceHistory);

        mockMvc.perform(post("/v1/price-history")
                .contentType(MediaType.APPLICATION_JSON)
                .content(priceHistoryJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.symbol", equalTo("AAPL")))
                .andExpect(jsonPath("$.close", equalTo(152.00)))
                .andExpect(jsonPath("$.open", equalTo(150.00)));

        verify(priceHistoryService, times(1)).savePriceHistory(any(PriceHistory.class));
    }

    @Test
    @DisplayName("Should return 400 when price history data is invalid")
    void testSavePriceHistoryInvalidData() throws Exception {
        String invalidJson = "{\"priceDate\": \"2024-09-24\"}";

        mockMvc.perform(post("/v1/price-history")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidJson))
                .andExpect(status().isBadRequest());

        verify(priceHistoryService, never()).savePriceHistory(any(PriceHistory.class));
    }

    @Test
    @DisplayName("Should handle error when saving price history")
    void testSavePriceHistoryError() throws Exception {
        when(priceHistoryService.savePriceHistory(any(PriceHistory.class)))
            .thenThrow(new RuntimeException("Database error"));

        String priceHistoryJson = objectMapper.writeValueAsString(testPriceHistory);

        mockMvc.perform(post("/v1/price-history")
                .contentType(MediaType.APPLICATION_JSON)
                .content(priceHistoryJson))
                .andExpect(status().isInternalServerError());

        verify(priceHistoryService, times(1)).savePriceHistory(any(PriceHistory.class));
    }

    // Field Validation Tests

    @Test
    @DisplayName("Should verify price history fields in response")
    void testPriceHistoryFieldsInResponse() throws Exception {
        List<PriceHistory> history = Arrays.asList(testPriceHistory);
        when(priceHistoryService.getPriceHistoryBySymbol("AAPL")).thenReturn(history);

        mockMvc.perform(get("/v1/price-history/AAPL")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].symbol", equalTo("AAPL")))
                .andExpect(jsonPath("$[0].priceDate", equalTo("2024-09-24")))
                .andExpect(jsonPath("$[0].open", equalTo(150.00)))
                .andExpect(jsonPath("$[0].high", equalTo(155.00)))
                .andExpect(jsonPath("$[0].low", equalTo(149.50)))
                .andExpect(jsonPath("$[0].close", equalTo(152.00)))
                .andExpect(jsonPath("$[0].volume", equalTo(1000000)));

        verify(priceHistoryService, times(1)).getPriceHistoryBySymbol("AAPL");
    }
}

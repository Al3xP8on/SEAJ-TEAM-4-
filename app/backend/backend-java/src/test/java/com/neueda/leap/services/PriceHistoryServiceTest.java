package com.neueda.leap.services;

import com.neueda.leap.models.PriceHistory;
import com.neueda.leap.repositories.PriceHistoryRepository;
import com.neueda.leap.validators.PriceHistoryValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("PriceHistoryService Tests")
public class PriceHistoryServiceTest {

    @Mock
    private PriceHistoryRepository priceHistoryRepository;

    @Mock
    private PriceHistoryValidator priceHistoryValidator;

    @InjectMocks
    private PriceHistoryService priceHistoryService;

    private PriceHistory testPriceHistory;
    private PriceHistory testPriceHistory2;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        // Mock validator to return symbol unchanged
        when(priceHistoryValidator.validateSymbol(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        doNothing().when(priceHistoryValidator).validateOHLCRelationships(any(), any(), any(), any());

        // Create test data using past dates
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

    // ==================== getPriceHistoryBySymbol Tests ====================

    @Test
    @DisplayName("Should get all price history records for a symbol successfully")
    void testGetPriceHistoryBySymbolSuccess() {
        List<PriceHistory> expectedHistory = Arrays.asList(testPriceHistory, testPriceHistory2);
        when(priceHistoryRepository.findBySymbol("AAPL")).thenReturn(expectedHistory);

        List<PriceHistory> result = priceHistoryService.getPriceHistoryBySymbol("AAPL");

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("AAPL", result.get(0).getSymbol());
        verify(priceHistoryRepository, times(1)).findBySymbol("AAPL");
    }

    @Test
    @DisplayName("Should return empty list when no price history found for symbol")
    void testGetPriceHistoryBySymbolEmpty() {
        when(priceHistoryRepository.findBySymbol("UNKNOWN")).thenReturn(Collections.emptyList());

        List<PriceHistory> result = priceHistoryService.getPriceHistoryBySymbol("UNKNOWN");

        assertNotNull(result);
        assertEquals(0, result.size());
        verify(priceHistoryRepository, times(1)).findBySymbol("UNKNOWN");
    }

    // ==================== getPriceHistoryBySymbolAndDateRange Tests ====================

    @Test
    @DisplayName("Should get price history records within date range successfully")
    void testGetPriceHistoryByDateRangeSuccess() {
        LocalDate fromDate = LocalDate.of(2024, 9, 24);
        LocalDate toDate = LocalDate.of(2024, 9, 25);
        List<PriceHistory> expectedHistory = Arrays.asList(testPriceHistory, testPriceHistory2);

        when(priceHistoryRepository.findBySymbolAndDateRange("AAPL", fromDate, toDate))
            .thenReturn(expectedHistory);

        List<PriceHistory> result = priceHistoryService.getPriceHistoryBySymbolAndDateRange("AAPL", fromDate, toDate);

        assertNotNull(result);
        assertEquals(2, result.size());
        verify(priceHistoryRepository, times(1)).findBySymbolAndDateRange("AAPL", fromDate, toDate);
    }

    @Test
    @DisplayName("Should return empty list when no price history found in date range")
    void testGetPriceHistoryByDateRangeEmpty() {
        LocalDate fromDate = LocalDate.of(2024, 10, 1);
        LocalDate toDate = LocalDate.of(2024, 10, 31);

        when(priceHistoryRepository.findBySymbolAndDateRange("AAPL", fromDate, toDate))
            .thenReturn(Collections.emptyList());

        List<PriceHistory> result = priceHistoryService.getPriceHistoryBySymbolAndDateRange("AAPL", fromDate, toDate);

        assertNotNull(result);
        assertEquals(0, result.size());
        verify(priceHistoryRepository, times(1)).findBySymbolAndDateRange("AAPL", fromDate, toDate);
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when fromDate is after toDate")
    void testGetPriceHistoryByDateRangeInvalidRange() {
        LocalDate fromDate = LocalDate.of(2024, 9, 25);
        LocalDate toDate = LocalDate.of(2024, 9, 24);

        assertThrows(IllegalArgumentException.class, () -> {
            priceHistoryService.getPriceHistoryBySymbolAndDateRange("AAPL", fromDate, toDate);
        });

        verify(priceHistoryRepository, never()).findBySymbolAndDateRange(any(), any(), any());
    }

    @Test
    @DisplayName("Should accept equal fromDate and toDate")
    void testGetPriceHistoryByDateRangeSameDate() {
        LocalDate date = LocalDate.of(2024, 9, 24);
        List<PriceHistory> expectedHistory = Arrays.asList(testPriceHistory);

        when(priceHistoryRepository.findBySymbolAndDateRange("AAPL", date, date))
            .thenReturn(expectedHistory);

        List<PriceHistory> result = priceHistoryService.getPriceHistoryBySymbolAndDateRange("AAPL", date, date);

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(priceHistoryRepository, times(1)).findBySymbolAndDateRange("AAPL", date, date);
    }

    // ==================== getLatestPriceHistory Tests ====================

    @Test
    @DisplayName("Should get the latest price history record for a symbol successfully")
    void testGetLatestPriceHistorySuccess() {
        when(priceHistoryRepository.findLatestBySymbol("AAPL")).thenReturn(testPriceHistory2);

        Optional<PriceHistory> result = priceHistoryService.getLatestPriceHistory("AAPL");

        assertTrue(result.isPresent());
        assertEquals("AAPL", result.get().getSymbol());
        assertEquals(new BigDecimal("156.00"), result.get().getClose());
        verify(priceHistoryRepository, times(1)).findLatestBySymbol("AAPL");
    }

    @Test
    @DisplayName("Should return empty Optional when no price history found for symbol")
    void testGetLatestPriceHistoryNotFound() {
        when(priceHistoryRepository.findLatestBySymbol("UNKNOWN")).thenReturn(null);

        Optional<PriceHistory> result = priceHistoryService.getLatestPriceHistory("UNKNOWN");

        assertFalse(result.isPresent());
        verify(priceHistoryRepository, times(1)).findLatestBySymbol("UNKNOWN");
    }

    // ==================== savePriceHistory Tests ====================

    @Test
    @DisplayName("Should save price history record successfully")
    void testSavePriceHistorySuccess() {
        when(priceHistoryRepository.save(any(PriceHistory.class))).thenReturn(testPriceHistory);

        PriceHistory result = priceHistoryService.savePriceHistory(testPriceHistory);

        assertNotNull(result);
        assertEquals("AAPL", result.getSymbol());
        assertEquals(LocalDate.of(2024, 9, 24), result.getPriceDate());
        verify(priceHistoryRepository, times(1)).save(testPriceHistory);
    }

    @Test
    @DisplayName("Should save multiple price history records")
    void testSaveMultiplePriceHistoryRecords() {
        when(priceHistoryRepository.save(testPriceHistory)).thenReturn(testPriceHistory);
        when(priceHistoryRepository.save(testPriceHistory2)).thenReturn(testPriceHistory2);

        PriceHistory result1 = priceHistoryService.savePriceHistory(testPriceHistory);
        PriceHistory result2 = priceHistoryService.savePriceHistory(testPriceHistory2);

        assertNotNull(result1);
        assertNotNull(result2);
        assertEquals("AAPL", result1.getSymbol());
        assertEquals("AAPL", result2.getSymbol());
        verify(priceHistoryRepository, times(2)).save(any(PriceHistory.class));
    }

    // ==================== Price Calculation Tests ====================

    @Test
    @DisplayName("Should calculate daily change correctly")
    void testDailyChangeCalculation() {
        PriceHistory history = new PriceHistory(
            "AAPL",
            LocalDate.of(2026, 9, 24),
            new BigDecimal("100.00"),
            new BigDecimal("110.00"),
            new BigDecimal("95.00"),
            new BigDecimal("105.00"),
            1000000L
        );

        BigDecimal dailyChange = history.getDailyChange();
        assertEquals(new BigDecimal("5.00"), dailyChange);
    }

    @Test
    @DisplayName("Should calculate daily change percent correctly")
    void testDailyChangePercentCalculation() {
        PriceHistory history = new PriceHistory(
            "AAPL",
            LocalDate.of(2026, 9, 24),
            new BigDecimal("100.00"),
            new BigDecimal("110.00"),
            new BigDecimal("95.00"),
            new BigDecimal("110.00"),
            1000000L
        );

        BigDecimal dailyChangePercent = history.getDailyChangePercent();
        assertEquals(new BigDecimal("10.00"), dailyChangePercent);
    }

    @Test
    @DisplayName("Should identify bullish price movement")
    void testIsBullish() {
        PriceHistory history = new PriceHistory(
            "AAPL",
            LocalDate.of(2026, 9, 24),
            new BigDecimal("100.00"),
            new BigDecimal("110.00"),
            new BigDecimal("95.00"),
            new BigDecimal("105.00"),
            1000000L
        );

        assertTrue(history.isBullish());
        assertFalse(history.isBearish());
        assertFalse(history.isNeutral());
    }

    @Test
    @DisplayName("Should identify bearish price movement")
    void testIsBearish() {
        PriceHistory history = new PriceHistory(
            "AAPL",
            LocalDate.of(2026, 9, 24),
            new BigDecimal("100.00"),
            new BigDecimal("110.00"),
            new BigDecimal("95.00"),
            new BigDecimal("95.00"),
            1000000L
        );

        assertTrue(history.isBearish());
        assertFalse(history.isBullish());
        assertFalse(history.isNeutral());
    }

    @Test
    @DisplayName("Should identify neutral price movement")
    void testIsNeutral() {
        PriceHistory history = new PriceHistory(
            "AAPL",
            LocalDate.of(2026, 9, 24),
            new BigDecimal("100.00"),
            new BigDecimal("110.00"),
            new BigDecimal("95.00"),
            new BigDecimal("100.00"),
            1000000L
        );

        assertTrue(history.isNeutral());
        assertFalse(history.isBullish());
        assertFalse(history.isBearish());
    }
}

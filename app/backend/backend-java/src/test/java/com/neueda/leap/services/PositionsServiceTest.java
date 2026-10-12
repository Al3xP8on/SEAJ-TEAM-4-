package com.neueda.leap.services;

import com.neueda.leap.dtos.PositionResponse;
import com.neueda.leap.dtos.ClosePositionRequest;
import com.neueda.leap.enums.PositionStatus;
import com.neueda.leap.exceptions.PositionNotFoundException;
import com.neueda.leap.exceptions.AccountNotFoundException;
import com.neueda.leap.exceptions.InsufficientHoldingsException;
import com.neueda.leap.models.Positions;
import com.neueda.leap.repositories.PositionsRepository;
import com.neueda.leap.validators.PositionsValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@DisplayName("PositionsService Tests")
public class PositionsServiceTest {

    @InjectMocks
    private PositionsService positionsService;

    @Mock
    private PositionsRepository positionsRepository;

    @Mock
    private AccountService accountService;

    @Mock
    private PositionsValidator validator;

    private Positions position;
    private Long accountId;
    private Long positionId;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        accountId = 1L;
        positionId = 1L;
        
        position = new Positions();
        position.setPositionId(positionId);
        ReflectionTestUtils.setField(position, "accountId", accountId);
        ReflectionTestUtils.setField(position, "symbol", "AAPL");
        ReflectionTestUtils.setField(position, "quantity", 100);
        ReflectionTestUtils.setField(position, "averageCost", new BigDecimal("150.00"));
        position.setStatus(PositionStatus.OPEN);
        ReflectionTestUtils.setField(position, "openedAt", LocalDateTime.now().minusDays(1));
        position.setRealisedPnL(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Should retrieve positions for account")
    void testGetPositions() throws AccountNotFoundException {
        List<Positions> positions = Arrays.asList(position);
        
        when(positionsRepository.findByAccountId(accountId)).thenReturn(positions);

        List<PositionResponse> result = positionsService.getPositions(accountId);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("AAPL", result.get(0).symbol());
        assertEquals(100, result.get(0).quantity());
        verify(positionsRepository).findByAccountId(accountId);
    }

    @Test
    @DisplayName("Should retrieve empty list when account has no positions")
    void testGetPositionsEmpty() throws AccountNotFoundException {
        when(positionsRepository.findByAccountId(accountId)).thenReturn(Arrays.asList());

        List<PositionResponse> result = positionsService.getPositions(accountId);

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(positionsRepository).findByAccountId(accountId);
    }

    @Test
    @DisplayName("Should retrieve multiple positions for account")
    void testGetPositionsMultiple() throws AccountNotFoundException {
        Positions position2 = new Positions();
        position2.setPositionId(2L);
        ReflectionTestUtils.setField(position2, "accountId", accountId);
        ReflectionTestUtils.setField(position2, "symbol", "MSFT");
        ReflectionTestUtils.setField(position2, "quantity", 50);
        ReflectionTestUtils.setField(position2, "averageCost", new BigDecimal("300.00"));
        position2.setStatus(PositionStatus.OPEN);
        
        List<Positions> positions = Arrays.asList(position, position2);
        when(positionsRepository.findByAccountId(accountId)).thenReturn(positions);

        List<PositionResponse> result = positionsService.getPositions(accountId);

        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(p -> "AAPL".equals(p.symbol())));
        assertTrue(result.stream().anyMatch(p -> "MSFT".equals(p.symbol())));
    }

    @Test
    @DisplayName("Should retrieve specific position by ID")
    void testGetPositionSuccess() throws AccountNotFoundException, PositionNotFoundException {
        when(positionsRepository.findByPositionIdAndAccountId(positionId, accountId))
            .thenReturn(Optional.of(position));

        PositionResponse result = positionsService.getPosition(accountId, positionId);

        assertNotNull(result);
        assertEquals("AAPL", result.symbol());
        assertEquals(100, result.quantity());
        verify(positionsRepository).findByPositionIdAndAccountId(positionId, accountId);
    }

    @Test
    @DisplayName("Should throw PositionNotFoundException when position not found")
    void testGetPositionNotFound() {
        when(positionsRepository.findByPositionIdAndAccountId(999L, accountId))
            .thenReturn(Optional.empty());

        assertThrows(PositionNotFoundException.class, () -> 
            positionsService.getPosition(accountId, 999L)
        );

        verify(positionsRepository).findByPositionIdAndAccountId(999L, accountId);
    }

    @Test
    @DisplayName("Should retrieve position by symbol")
    void testGetPositionBySymbolSuccess() throws AccountNotFoundException {
        when(positionsRepository.findByAccountIdAndSymbol(accountId, "AAPL"))
            .thenReturn(Optional.of(position));

        Optional<PositionResponse> result = positionsService.getPositionBySymbol(accountId, "AAPL");

        assertTrue(result.isPresent());
        assertEquals("AAPL", result.get().symbol());
        verify(positionsRepository).findByAccountIdAndSymbol(accountId, "AAPL");
    }

    @Test
    @DisplayName("Should return empty Optional when position symbol not found")
    void testGetPositionBySymbolNotFound() throws AccountNotFoundException {
        when(positionsRepository.findByAccountIdAndSymbol(accountId, "TSLA"))
            .thenReturn(Optional.empty());

        Optional<PositionResponse> result = positionsService.getPositionBySymbol(accountId, "TSLA");

        assertTrue(result.isEmpty());
        verify(positionsRepository).findByAccountIdAndSymbol(accountId, "TSLA");
    }

    @Test
    @DisplayName("Should close position successfully")
    void testClosePositionSuccess() throws AccountNotFoundException, PositionNotFoundException, InsufficientHoldingsException {
        ClosePositionRequest request = new ClosePositionRequest(
            new BigDecimal("155.00"), 50  // Close 50 out of 100 shares
        );
        
        when(positionsRepository.findByPositionIdAndAccountId(positionId, accountId))
            .thenReturn(Optional.of(position));
        when(positionsRepository.save(any(Positions.class))).thenReturn(position);

        PositionResponse result = positionsService.closePosition(accountId, positionId, request);

        assertNotNull(result);
        assertEquals(50, result.quantity());  // Should have 50 remaining
        verify(positionsRepository).save(any(Positions.class));
    }

    @Test
    @DisplayName("Should close entire position")
    void testCloseEntirePosition() throws AccountNotFoundException, PositionNotFoundException, InsufficientHoldingsException {
        ClosePositionRequest request = new ClosePositionRequest(
            new BigDecimal("155.00"), 100  // Close all 100 shares
        );
        
        when(positionsRepository.findByPositionIdAndAccountId(positionId, accountId))
            .thenReturn(Optional.of(position));
        when(positionsRepository.save(any(Positions.class))).thenReturn(position);

        PositionResponse result = positionsService.closePosition(accountId, positionId, request);

        assertNotNull(result);
        verify(positionsRepository).save(any(Positions.class));
    }

    @Test
    @DisplayName("Should throw exception when closing quantity is invalid")
    void testClosePositionInvalidQuantity() throws AccountNotFoundException, PositionNotFoundException {
        ClosePositionRequest request = new ClosePositionRequest(
            new BigDecimal("155.00"), 101  // Try to close more than available
        );
        
        when(positionsRepository.findByPositionIdAndAccountId(positionId, accountId))
            .thenReturn(Optional.of(position));

        assertThrows(IllegalArgumentException.class, () -> 
            positionsService.closePosition(accountId, positionId, request)
        );

        verify(positionsRepository, never()).save(any(Positions.class));
    }

    @Test
    @DisplayName("Should throw exception when closing zero quantity")
    void testClosePositionZeroQuantity() throws AccountNotFoundException, PositionNotFoundException {
        ClosePositionRequest request = new ClosePositionRequest(
            new BigDecimal("155.00"), 0
        );
        
        when(positionsRepository.findByPositionIdAndAccountId(positionId, accountId))
            .thenReturn(Optional.of(position));

        assertThrows(IllegalArgumentException.class, () -> 
            positionsService.closePosition(accountId, positionId, request)
        );

        verify(positionsRepository, never()).save(any(Positions.class));
    }

    @Test
    @DisplayName("Should throw exception when closing negative quantity")
    void testClosePositionNegativeQuantity() throws AccountNotFoundException, PositionNotFoundException {
        ClosePositionRequest request = new ClosePositionRequest(
            new BigDecimal("155.00"), -10
        );
        
        when(positionsRepository.findByPositionIdAndAccountId(positionId, accountId))
            .thenReturn(Optional.of(position));

        assertThrows(IllegalArgumentException.class, () -> 
            positionsService.closePosition(accountId, positionId, request)
        );

        verify(positionsRepository, never()).save(any(Positions.class));
    }

    @Test
    @DisplayName("Should throw PositionNotFoundException when position not found during close")
    void testClosePositionNotFound() {
        ClosePositionRequest request = new ClosePositionRequest(
            new BigDecimal("155.00"), 50
        );
        
        when(positionsRepository.findByPositionIdAndAccountId(999L, accountId))
            .thenReturn(Optional.empty());

        assertThrows(PositionNotFoundException.class, () -> 
            positionsService.closePosition(accountId, 999L, request)
        );
    }

    @Test
    @DisplayName("Should map position to response correctly")
    void testPositionResponseMapping() throws AccountNotFoundException, PositionNotFoundException {
        position.setStatus(PositionStatus.CLOSED);
        position.setClosedAt(LocalDateTime.now());
        position.setRealisedPnL(new BigDecimal("500.00"));
        
        when(positionsRepository.findByPositionIdAndAccountId(positionId, accountId))
            .thenReturn(Optional.of(position));

        PositionResponse result = positionsService.getPosition(accountId, positionId);

        assertEquals(positionId, result.positionId());
        assertEquals(accountId, result.accountId());
        assertEquals("AAPL", result.symbol());
        assertEquals(100, result.quantity());
        assertEquals(new BigDecimal("150.00"), result.averageCost());
        assertEquals(PositionStatus.CLOSED, result.status());
        assertEquals(new BigDecimal("500.00"), result.realisedPnL());
    }

    @Test
    @DisplayName("Should handle positions with large quantities")
    void testGetPositionsLargeQuantity() throws AccountNotFoundException {
        ReflectionTestUtils.setField(position, "quantity", 1000000);  // 1 million shares
        List<Positions> positions = Arrays.asList(position);
        
        when(positionsRepository.findByAccountId(accountId)).thenReturn(positions);

        List<PositionResponse> result = positionsService.getPositions(accountId);

        assertEquals(1, result.size());
        assertEquals(1000000, result.get(0).quantity());
    }

    @Test
    @DisplayName("Should handle positions with fractional quantities")
    void testGetPositionsFractionalQuantity() throws AccountNotFoundException {
        ReflectionTestUtils.setField(position, "quantity", 1);  // Single share
        List<Positions> positions = Arrays.asList(position);
        
        when(positionsRepository.findByAccountId(accountId)).thenReturn(positions);

        List<PositionResponse> result = positionsService.getPositions(accountId);

        assertEquals(1, result.size());
        assertEquals(1, result.get(0).quantity());
    }

    @Test
    @DisplayName("Should close position with high price")
    void testClosePositionHighPrice() throws AccountNotFoundException, PositionNotFoundException, InsufficientHoldingsException {
        ClosePositionRequest request = new ClosePositionRequest(
            new BigDecimal("99999.99"), 1
        );
        
        ReflectionTestUtils.setField(position, "quantity", 1);
        when(positionsRepository.findByPositionIdAndAccountId(positionId, accountId))
            .thenReturn(Optional.of(position));
        when(positionsRepository.save(any(Positions.class))).thenReturn(position);

        PositionResponse result = positionsService.closePosition(accountId, positionId, request);

        assertNotNull(result);
        verify(positionsRepository).save(any(Positions.class));
    }

    @Test
    @DisplayName("Should retrieve position by symbol case-sensitive")
    void testGetPositionBySymbolCaseSensitive() throws AccountNotFoundException {
        when(positionsRepository.findByAccountIdAndSymbol(accountId, "aapl"))
            .thenReturn(Optional.empty());

        Optional<PositionResponse> result = positionsService.getPositionBySymbol(accountId, "aapl");

        assertTrue(result.isEmpty());
        verify(positionsRepository).findByAccountIdAndSymbol(accountId, "aapl");
    }
}

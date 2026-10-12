package com.neueda.leap.controllers;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.neueda.leap.services.PositionsService;
import com.neueda.leap.dtos.*;
import com.neueda.leap.enums.PositionStatus;
import com.neueda.leap.exceptions.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import static org.mockito.Mockito.*;

class PositionsControllerTest {
    private final PositionsService service = mock(PositionsService.class);
    private final PositionResponse position = new PositionResponse(2L, 1L, "AAPL", 3, BigDecimal.TEN, PositionStatus.OPEN, LocalDateTime.of(2026, 1, 1, 12, 0), null, BigDecimal.ZERO);
    private final ClosePositionRequest request = new ClosePositionRequest(BigDecimal.TEN, 3);
    private PositionsController controller() {
        PositionsController controller = new PositionsController();
        ReflectionTestUtils.setField(controller, "positionsService", service);
        return controller;
    }
    @Test void rootDescribesAccountPositionsRoute() {
        assertEquals(Map.of("message", "Use /{accountId}/positions to get positions for an account"), controller().getRootPositions().getBody());
        verifyNoInteractions(service);
    }
    @Test void returnsPositionsAndClosedPosition() throws Exception {
        when(service.getPositions(1L)).thenReturn(List.of(position));
        when(service.getPosition(1L, 2L)).thenReturn(position);
        when(service.getPositionBySymbol(1L, "AAPL")).thenReturn(Optional.of(position));
        when(service.closePosition(1L, 2L, request)).thenReturn(position);
        PositionsController controller = controller();
        assertEquals(List.of(position), controller.getPositions(1L).getBody());
        assertSame(position, controller.getPosition(1L, 2L).getBody());
        assertSame(position, controller.getPositionBySymbol(1L, "AAPL").getBody());
        assertSame(position, controller.closePosition(1L, 2L, request).getBody());
    }
    @Test void absentSymbolReturnsNoContent() throws Exception {
        when(service.getPositionBySymbol(1L, "AAPL")).thenReturn(Optional.empty());
        var response = controller().getPositionBySymbol(1L, "AAPL");
        assertEquals(204, response.getStatusCode().value());
        assertNull(response.getBody());
    }
    @Test void mapsMissingAccountsAcrossEndpoints() throws Exception {
        when(service.getPositions(1L)).thenThrow(new AccountNotFoundException("missing"));
        when(service.getPosition(1L, 2L)).thenThrow(new AccountNotFoundException("missing"));
        when(service.getPositionBySymbol(1L, "AAPL")).thenThrow(new AccountNotFoundException("missing"));
        when(service.closePosition(1L, 2L, request)).thenThrow(new AccountNotFoundException("missing"));
        PositionsController controller = controller();
        for (var response : List.of(controller.getPositions(1L), controller.getPosition(1L, 2L), controller.getPositionBySymbol(1L, "AAPL"), controller.closePosition(1L, 2L, request))) {
            assertEquals(404, response.getStatusCode().value());
            assertEquals("Account 1 not found", response.getBody());
        }
    }
    @Test void mapsMissingPositionsToNotFound() throws Exception {
        when(service.getPosition(1L, 2L)).thenThrow(new PositionNotFoundException("missing position"));
        when(service.closePosition(1L, 2L, request)).thenThrow(new PositionNotFoundException("missing position"));
        for (var response : List.of(controller().getPosition(1L, 2L), controller().closePosition(1L, 2L, request))) {
            assertEquals(404, response.getStatusCode().value());
            assertEquals("missing position", response.getBody());
        }
    }
    @Test void mapsInvalidCloseRequestsAndInsufficientHoldings() throws Exception {
        when(service.closePosition(1L, 2L, request)).thenThrow(new IllegalArgumentException("invalid quantity"));
        var invalid = controller().closePosition(1L, 2L, request);
        assertEquals(400, invalid.getStatusCode().value());
        assertEquals("invalid quantity", invalid.getBody());
        doThrow(new InsufficientHoldingsException("too many shares")).when(service).closePosition(1L, 2L, request);
        var insufficient = controller().closePosition(1L, 2L, request);
        assertEquals(400, insufficient.getStatusCode().value());
        assertEquals("Cannot close position: too many shares", insufficient.getBody());
    }
    @Test void mapsUnexpectedFailuresAcrossEndpoints() throws Exception {
        when(service.getPositions(1L)).thenThrow(new IllegalStateException("offline"));
        when(service.getPosition(1L, 2L)).thenThrow(new IllegalStateException("offline"));
        when(service.getPositionBySymbol(1L, "AAPL")).thenThrow(new IllegalStateException("offline"));
        when(service.closePosition(1L, 2L, request)).thenThrow(new IllegalStateException("offline"));
        PositionsController controller = controller();
        var list = controller.getPositions(1L);
        assertEquals(500, list.getStatusCode().value());
        assertEquals("Error retrieving positions: offline", list.getBody());
        for (var response : List.of(controller.getPosition(1L, 2L), controller.getPositionBySymbol(1L, "AAPL"))) {
            assertEquals(500, response.getStatusCode().value());
            assertEquals("Error retrieving position: offline", response.getBody());
        }
        var close = controller.closePosition(1L, 2L, request);
        assertEquals(500, close.getStatusCode().value());
        assertEquals("Error closing position: offline", close.getBody());
    }
}

package com.neueda.leap.controllers;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.neueda.leap.dtos.InstrumentResponse;
import com.neueda.leap.services.InstrumentsService;
import com.neueda.leap.models.Instrument;
import java.util.List;
import java.util.Optional;
import static org.mockito.Mockito.*;

class InstrumentsControllerTest {
    private final InstrumentsService service = mock(InstrumentsService.class);
    private final InstrumentsController controller = new InstrumentsController(service);
    private final Instrument instrument = new Instrument("AAPL", "Apple", "Equity", "USD", true);
    private final InstrumentResponse expected = new InstrumentResponse("AAPL", "Apple", "Equity", "USD", true);
    @Test void appliesCombinedFilterBeforeOtherFilters() {
        when(service.getInstrumentsByAssetClassAndTradable("Equity", false)).thenReturn(List.of(instrument));
        assertEquals(List.of(expected), controller.getAllInstruments(false, "Equity", "USD").getBody());
        verify(service).getInstrumentsByAssetClassAndTradable("Equity", false);
        verifyNoMoreInteractions(service);
    }
    @Test void selectsAssetClassBeforeCurrency() {
        when(service.getInstrumentsByAssetClass("Equity")).thenReturn(List.of(instrument));
        assertEquals(List.of(expected), controller.getAllInstruments(null, "Equity", "USD").getBody());
        verify(service).getInstrumentsByAssetClass("Equity");
        verifyNoMoreInteractions(service);
    }
    @Test void selectsCurrencyBeforeTradable() {
        when(service.getInstrumentsByCurrency("USD")).thenReturn(List.of(instrument));
        assertEquals(List.of(expected), controller.getAllInstruments(true, null, "USD").getBody());
        verify(service).getInstrumentsByCurrency("USD");
        verifyNoMoreInteractions(service);
    }
    @Test void routesUnfilteredAndTradableRequests() {
        when(service.getAllInstruments()).thenReturn(List.of(instrument));
        when(service.getTradableInstruments()).thenReturn(List.of(instrument));
        assertEquals(200, controller.getAllInstruments(null, null, null).getStatusCode().value());
        assertEquals(List.of(expected), controller.getAllInstruments(false, null, null).getBody());
        assertEquals(List.of(expected), controller.getAllInstruments(true, null, null).getBody());
        verify(service, times(2)).getAllInstruments();
        verify(service).getTradableInstruments();
    }
    @Test void returnsInstrumentOrStructuredNotFoundResponse() {
        when(service.getInstrumentBySymbol("AAPL")).thenReturn(Optional.of(instrument));
        when(service.getInstrumentBySymbol("MISSING")).thenReturn(Optional.empty());
        assertEquals(expected, controller.getInstrumentBySymbol("AAPL").getBody());
        var response = controller.getInstrumentBySymbol("MISSING");
        assertEquals(404, response.getStatusCode().value());
        var body = (InstrumentsController.ErrorResponse) response.getBody();
        assertEquals(404, body.getStatus());
        assertEquals("Instrument not found: MISSING", body.getMessage());
    }
}

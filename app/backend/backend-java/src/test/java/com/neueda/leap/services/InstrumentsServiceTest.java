package com.neueda.leap.services;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.neueda.leap.models.Instrument;
import com.neueda.leap.repositories.InstrumentsRepository;
import com.neueda.leap.validators.InstrumentsValidator;
import java.util.List;
import java.util.Optional;
import static org.mockito.Mockito.*;

class InstrumentsServiceTest {
    private final InstrumentsRepository repository = mock(InstrumentsRepository.class);
    private final InstrumentsService service = new InstrumentsService(repository, new InstrumentsValidator());
    private final Instrument instrument = new Instrument("AAPL", "Apple", "Equity", "USD", true);
    @Test void returnsAllAndTradableInstruments() {
        List<Instrument> results = List.of(instrument);
        when(repository.findAll()).thenReturn(results);
        when(repository.findByTradable(true)).thenReturn(results);
        assertSame(results, service.getAllInstruments());
        assertSame(results, service.getTradableInstruments());
    }
    @Test void validatesAndNormalizesQueryArguments() {
        List<Instrument> results = List.of(instrument);
        when(repository.findBySymbol("AAPL")).thenReturn(Optional.of(instrument));
        when(repository.findByAssetClass("Equity")).thenReturn(results);
        when(repository.findByCurrency("USD")).thenReturn(results);
        when(repository.findByAssetClassAndTradable("Equity", false)).thenReturn(results);
        assertEquals(Optional.of(instrument), service.getInstrumentBySymbol(" AAPL "));
        assertSame(results, service.getInstrumentsByAssetClass(" Equity "));
        assertSame(results, service.getInstrumentsByCurrency(" usd "));
        assertSame(results, service.getInstrumentsByAssetClassAndTradable(" Equity ", false));
    }
    @Test void rejectsInvalidQueriesBeforeRepositoryAccess() {
        assertThrows(IllegalArgumentException.class, () -> service.getInstrumentBySymbol(" "));
        assertThrows(IllegalArgumentException.class, () -> service.getInstrumentsByAssetClass(" "));
        assertThrows(IllegalArgumentException.class, () -> service.getInstrumentsByCurrency("US"));
        assertThrows(NullPointerException.class, () -> service.getInstrumentsByAssetClassAndTradable(null, true));
        verifyNoInteractions(repository);
    }
    @Test void missingSymbolReturnsEmpty() {
        when(repository.findBySymbol("MISSING")).thenReturn(Optional.empty());
        assertTrue(service.getInstrumentBySymbol("MISSING").isEmpty());
    }
}

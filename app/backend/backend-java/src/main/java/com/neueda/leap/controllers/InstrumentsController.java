package com.neueda.leap.controllers;

import com.neueda.leap.models.Instrument;
import com.neueda.leap.services.InstrumentsService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/instruments")
public class InstrumentsController {
    private final InstrumentsService instrumentsService;

    public InstrumentsController(InstrumentsService instrumentsService) {
        this.instrumentsService = instrumentsService;
    }

    @GetMapping
    public ResponseEntity<List<Instrument>> getAllInstruments(
            @RequestParam(value = "tradable", required = false) Boolean tradable,
            @RequestParam(value = "assetClass", required = false) String assetClass,
            @RequestParam(value = "currency", required = false) String currency) {
        
        List<Instrument> instruments;
        
        if (assetClass != null && tradable != null) {
            instruments = instrumentsService.getInstrumentsByAssetClassAndTradable(assetClass, tradable);
        } else if (assetClass != null) {
            instruments = instrumentsService.getInstrumentsByAssetClass(assetClass);
        } else if (currency != null) {
            instruments = instrumentsService.getInstrumentsByCurrency(currency);
        } else if (tradable != null) {
            instruments = tradable ? instrumentsService.getTradableInstruments() : instrumentsService.getAllInstruments();
        } else {
            instruments = instrumentsService.getAllInstruments();
        }
        
        return ResponseEntity.ok(instruments);
    }

    @GetMapping("/{symbol}")
    public ResponseEntity<?> getInstrumentBySymbol(@PathVariable String symbol) {
        Optional<Instrument> instrument = instrumentsService.getInstrumentBySymbol(symbol);
        
        if (instrument.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(HttpStatus.NOT_FOUND.value(), "Instrument not found: " + symbol));
        }
        return ResponseEntity.ok(instrument.get());
    }

    public static class ErrorResponse {
        private int status;
        private String message;

        public ErrorResponse(int status, String message) {
            this.status = status;
            this.message = message;
        }

        public int getStatus() {
            return status;
        }

        public String getMessage() {
            return message;
        }
    }
}

package com.neueda.leap.controllers;

import com.neueda.leap.services.ExchangeRatesService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/market-data")
public class ExchangeRatesController {
    private final ExchangeRatesService rates;
    public ExchangeRatesController(ExchangeRatesService rates) { this.rates = rates; }
    @GetMapping("/fx-history")
    public ExchangeRatesService.History history() { return rates.history(); }
    @GetMapping("/fx-rates")
    public ExchangeRatesService.Rates latest() { return rates.latest(); }
}

package com.neueda.leap.controllers;

import com.neueda.leap.services.AlpacaMarketDataService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping(value = "/v1/market-data", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Market data", description = "Alpaca market overview and price bars")
public class MarketDataController {
    private final AlpacaMarketDataService market;
    public MarketDataController(AlpacaMarketDataService market) { this.market = market; }

    @GetMapping("/overview")
    public AlpacaMarketDataService.Overview overview() { return market.overview(); }

    /** Public, for the landing page: symbol, label, price and daily change for a short fixed list */
    @GetMapping("/ticker")
    public List<AlpacaMarketDataService.TickerQuote> ticker() { return market.ticker(); }

    @GetMapping("/{symbol}/bars")
    public AlpacaMarketDataService.Chart bars(@PathVariable String symbol,
            @RequestParam(defaultValue = "1Min") String timeframe) {
        return market.bars(symbol, timeframe);
    }
}

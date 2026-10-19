package com.neueda.leap.controllers;

import com.neueda.leap.services.AlpacaMarketDataService;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/v1/market-data")
public class MarketDataController {
    private final AlpacaMarketDataService market;
    public MarketDataController(AlpacaMarketDataService market) { this.market = market; }

    @GetMapping("/overview")
    public AlpacaMarketDataService.Overview overview() { return market.overview(); }

    /** Public, for the landing page: symbol, label, price and daily change for all priced instruments */
    @GetMapping("/ticker")
    public List<AlpacaMarketDataService.TickerQuote> ticker() { return market.ticker(); }

    @GetMapping("/{symbol}/bars")
    public AlpacaMarketDataService.Chart bars(@PathVariable String symbol,
            @RequestParam(defaultValue = "1Min") String timeframe) {
        return market.bars(symbol, timeframe);
    }
}

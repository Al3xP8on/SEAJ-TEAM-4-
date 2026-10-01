package com.neueda.leap.interfaces;

import com.neueda.leap.messaging.MarketPrice;

public interface MarketDataClient {
    MarketPrice getLatestMarketPrice(String symbol);
}
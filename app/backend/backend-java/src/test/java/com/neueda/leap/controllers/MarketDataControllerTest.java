package com.neueda.leap.controllers;

import com.neueda.leap.services.AlpacaMarketDataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MarketDataControllerTest {
    private final AlpacaMarketDataService market = mock(AlpacaMarketDataService.class);
    private MockMvc mvc;
    @BeforeEach void setup() { mvc = MockMvcBuilders.standaloneSetup(new MarketDataController(market)).build(); }

    @Test void returnsConnectionStateWithoutExposingCredentials() throws Exception {
        when(market.overview()).thenReturn(new AlpacaMarketDataService.Overview(false, "iex", null,
                "Alpaca market data is not connected yet.", List.of(), List.of()));
        mvc.perform(get("/v1/market-data/overview")).andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(false)).andExpect(jsonPath("$.feed").value("iex"))
                .andExpect(jsonPath("$.snapshots").isArray()).andExpect(jsonPath("$.keyId").doesNotExist())
                .andExpect(jsonPath("$.secretKey").doesNotExist());
    }
    @Test void defaultsToIntradayHistoryAndPassesTheSelectedTimeframe() throws Exception {
        when(market.bars("AAPL", "1Min")).thenReturn(new AlpacaMarketDataService.Chart("AAPL", "iex", "1Min", List.of(), ""));
        when(market.bars("AAPL", "1Day")).thenReturn(new AlpacaMarketDataService.Chart("AAPL", "iex", "1Day", List.of(), ""));
        when(market.bars("AAPL", "6Hour")).thenReturn(new AlpacaMarketDataService.Chart("AAPL", "iex", "6Hour", List.of(), ""));
        mvc.perform(get("/v1/market-data/AAPL/bars")).andExpect(status().isOk()).andExpect(jsonPath("$.timeframe").value("1Min"));
        mvc.perform(get("/v1/market-data/AAPL/bars").param("timeframe", "1Day")).andExpect(status().isOk()).andExpect(jsonPath("$.timeframe").value("1Day"));
        mvc.perform(get("/v1/market-data/AAPL/bars").param("timeframe", "6Hour")).andExpect(status().isOk()).andExpect(jsonPath("$.timeframe").value("6Hour"));
        verify(market).bars("AAPL", "1Min");
        verify(market).bars("AAPL", "1Day");
        verify(market).bars("AAPL", "6Hour");
    }
}

package com.neueda.leap.controllers;
import com.neueda.leap.services.AlpacaBondService;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/v1/market-data")
public class BondMarketController {
    private final AlpacaBondService bonds;
    public BondMarketController(AlpacaBondService bonds) { this.bonds = bonds; }
    @GetMapping("/bonds")
    public AlpacaBondService.Overview overview() { return bonds.overview(); }
}

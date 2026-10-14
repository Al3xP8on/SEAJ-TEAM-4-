package com.neueda.leap.dtos;

import com.neueda.leap.models.Instrument;

public record InstrumentResponse(
    String symbol,
    String name,
    String assetClass,
    String currency,
    boolean tradable
) {
    public static InstrumentResponse from(Instrument instrument) {
        return new InstrumentResponse(
            instrument.getSymbol(),
            instrument.getName(),
            instrument.getAssetClass(),
            instrument.getCurrency(),
            instrument.isTradable()
        );
    }
}

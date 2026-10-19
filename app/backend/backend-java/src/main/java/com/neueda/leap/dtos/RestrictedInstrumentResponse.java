package com.neueda.leap.dtos;

import com.neueda.leap.models.RestrictedInstrument;

import java.time.LocalDateTime;

public record RestrictedInstrumentResponse(String symbol, String name, String reason, String addedBy, LocalDateTime addedOn) {
    public static RestrictedInstrumentResponse from(RestrictedInstrument entry, String name) {
        return new RestrictedInstrumentResponse(entry.getSymbol(), name, entry.getReason(), entry.getAddedBy(), entry.getAddedOn());
    }
}

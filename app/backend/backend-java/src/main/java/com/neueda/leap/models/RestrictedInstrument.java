package com.neueda.leap.models;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** An instrument no customer may buy, with the reason shown to them. */
@Entity
@Table(name = "restricted_instruments")
public class RestrictedInstrument {
    @Id
    @Column(length = 20)
    private String symbol;

    @Column(nullable = false)
    private String reason;

    @Column(name = "added_by", nullable = false, length = 100)
    private String addedBy;

    @Column(name = "added_on", nullable = false)
    private LocalDateTime addedOn;

    protected RestrictedInstrument() {
    }

    public RestrictedInstrument(String symbol, String reason, String addedBy) {
        this.symbol = symbol;
        this.reason = reason;
        this.addedBy = addedBy;
        this.addedOn = LocalDateTime.now();
    }

    public String getSymbol() { return symbol; }
    public String getReason() { return reason; }
    public String getAddedBy() { return addedBy; }
    public LocalDateTime getAddedOn() { return addedOn; }
}

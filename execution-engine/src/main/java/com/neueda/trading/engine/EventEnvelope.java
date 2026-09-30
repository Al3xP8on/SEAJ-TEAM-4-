package com.neueda.trading.engine;
import com.neueda.trading.enums.EventType;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record EventEnvelope(
        UUID eventId,
        EventType eventType, 
        Instant timestamp,
        int version,
        Map<String, Object> payload
) {}

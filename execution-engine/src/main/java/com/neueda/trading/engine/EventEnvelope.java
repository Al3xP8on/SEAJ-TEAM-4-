package com.neueda.trading.engine;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record EventEnvelope(
        UUID eventId,
        String eventType, 
        Instant timestamp,
        int version,
        Map<String, Object> payload
) {}

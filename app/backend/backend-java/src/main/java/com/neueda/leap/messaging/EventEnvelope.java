package com.neueda.leap.messaging;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Generic event envelope for Kafka messaging.
 * Wraps any event payload with metadata like event ID, type, timestamp, and version.
 */
public record EventEnvelope(
        UUID eventId,
        EventType eventType, 
        Instant timestamp,
        int version,
        Map<String, Object> payload
) {}

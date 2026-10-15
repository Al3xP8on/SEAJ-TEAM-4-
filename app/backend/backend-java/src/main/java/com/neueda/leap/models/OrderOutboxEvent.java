package com.neueda.leap.models;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** Stored in the same transaction as the order. Retries retain the original JSON/event ID. */
@Entity
@Table(name = "order_event_outbox")
public class OrderOutboxEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "event_id", nullable = false, unique = true)
    private UUID eventId;
    @Column(nullable = false, length = 100)
    private String topic;
    @Column(name = "message_key", nullable = false, length = 100)
    private String messageKey;
    @Column(nullable = false, columnDefinition = "text")
    private String payload;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "published_at")
    private Instant publishedAt;

    protected OrderOutboxEvent() { }
    public OrderOutboxEvent(UUID eventId, String topic, String messageKey, String payload) {
        this.eventId = eventId;
        this.topic = topic;
        this.messageKey = messageKey;
        this.payload = payload;
        this.createdAt = Instant.now();
    }
    public Long getId() { return id; }
    public UUID getEventId() { return eventId; }
    public String getTopic() { return topic; }
    public String getMessageKey() { return messageKey; }
    public String getPayload() { return payload; }
    public Instant getPublishedAt() { return publishedAt; }
    public void markPublished() { publishedAt = Instant.now(); }
}

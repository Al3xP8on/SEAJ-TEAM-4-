package com.neueda.leap.messaging;

// A Kafka message that cant be processed - sent to respective dead-letter topic.
public class PoisonMessageException extends RuntimeException {

    public PoisonMessageException(String message) {
        super(message);
    }

    public PoisonMessageException(String message, Throwable cause) {
        super(message, cause);
    }
}

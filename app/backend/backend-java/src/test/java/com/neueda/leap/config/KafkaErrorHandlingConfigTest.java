package com.neueda.leap.config;

import com.fasterxml.jackson.core.JsonParseException;
import com.neueda.leap.messaging.PoisonMessageException;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.core.NestedRuntimeException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.ListenerExecutionFailedException;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.support.SendResult;

import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KafkaErrorHandlingConfigTest {

    private static final int MAX_RETRIES = 4;
    private static final int RUNAWAY_RETRY_LIMIT = 100;

    private KafkaTemplate<String, String> template;
    private Consumer<?, ?> consumer;
    private MessageListenerContainer container;
    private DefaultErrorHandler handler;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        template = mock(KafkaTemplate.class);
        when(template.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(new SendResult<>(null, null)));
        consumer = mock(Consumer.class);
        container = mock(MessageListenerContainer.class);
        handler = new KafkaErrorHandlingConfig().kafkaErrorHandler(template, "market-data");
    }

    static Stream<Exception> neverSucceeds() {
        return Stream.of(
                new PoisonMessageException("Order not found"),
                new JsonParseException(null, "Unexpected character"),
                new NumberFormatException("abc"),
                new IllegalArgumentException("Invalid UUID string"),
                new NullPointerException(),
                new DateTimeParseException("bad", "x", 0),
                new ClassCastException());
    }

    @ParameterizedTest
    @MethodSource("neverSucceeds")
    void testMessageThatNeverSucceeds_IsDeadLetteredOnFirstAttempt(Exception cause) {
        ConsumerRecord<String, String> record = new ConsumerRecord<>("trades", 1, 42L, "ACC-1", "{bad");

        handle(record, new RuntimeException("Failed to process ORDER_PLACED event", cause));

        ProducerRecord<String, String> deadLetter = sentDeadLetter();
        assertEquals("trades.DLT", deadLetter.topic());
        assertEquals("ACC-1", deadLetter.key());
        assertEquals("{bad", deadLetter.value());
        assertNotNull(deadLetter.headers().lastHeader("kafka_dlt-original-topic"));
        assertNotNull(deadLetter.headers().lastHeader("kafka_dlt-exception-fqcn"));
    }

    @Test
    void testMessageThatMaySucceedLater_IsRetriedThenDeadLettered() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>("trade-events", 0, 7L, "ACC-1", "{}");
        Exception outage = new DataAccessResourceFailureException("database unavailable");

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            assertThrows(NestedRuntimeException.class, () -> handle(record, outage),
                    "attempt " + attempt + " should seek back for a retry");
            verify(template, never()).send(any(ProducerRecord.class));
        }

        handle(record, outage);

        assertEquals("trade-events.DLT", sentDeadLetter().topic());
    }

    @Test
    void testRetryableMessage_IsNotRetriedIndefinitely() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>("executions", 0, 3L, "ACC-1", "{}");
        Exception outage = new IllegalStateException("downstream unavailable");

        int failedAttempts = 0;
        while (failedAttempts < RUNAWAY_RETRY_LIMIT) {
            try {
                handle(record, outage);
                break;
            } catch (NestedRuntimeException retry) {
                failedAttempts++;
            }
        }

        assertEquals(MAX_RETRIES, failedAttempts);
        verify(template, times(1)).send(any(ProducerRecord.class));
    }

    @Test
    void testMarketDataFailure_IsDeadLetteredWithoutRetry() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>("market-data", 0, 5L, "AAPL", "{}");

        handle(record, new DataAccessResourceFailureException("database unavailable"));

        assertEquals("market-data.DLT", sentDeadLetter().topic());
    }

    private void handle(ConsumerRecord<String, String> record, Exception cause) {
        handler.handleRemaining(new ListenerExecutionFailedException("Listener failed", cause),
                List.of(record), consumer, container);
    }

    @SuppressWarnings("unchecked")
    private ProducerRecord<String, String> sentDeadLetter() {
        ArgumentCaptor<ProducerRecord<String, String>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(template, times(1)).send(captor.capture());
        return captor.getValue();
    }
}

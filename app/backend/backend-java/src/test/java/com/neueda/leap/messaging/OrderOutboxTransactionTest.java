package com.neueda.leap.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.neueda.leap.enums.*;
import com.neueda.leap.models.*;
import com.neueda.leap.models.Order;
import com.neueda.leap.repositories.OrderOutboxRepository;
import org.junit.jupiter.api.*;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real database transactions verify rollback and the persisted Kafka retry queue. */
class OrderOutboxTransactionTest {
    static LocalContainerEntityManagerFactoryBean factory;
    static TransactionTemplate tx;
    static OrderOutboxRepository repository;
    OrderEventOutbox outbox;
    @SuppressWarnings("unchecked") final KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);

    @BeforeAll static void database() {
        factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(new DriverManagerDataSource("jdbc:h2:mem:outbox;DB_CLOSE_DELAY=-1", "sa", ""));
        factory.setManagedTypes(PersistenceManagedTypes.of(OrderOutboxEvent.class.getName()));
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "create-drop"));
        factory.afterPropertiesSet();
        var emf = Objects.requireNonNull(factory.getObject());
        tx = new TransactionTemplate(new JpaTransactionManager(emf));
        repository = new JpaRepositoryFactory(SharedEntityManagerCreator.createSharedEntityManager(emf))
                .getRepository(OrderOutboxRepository.class);
    }
    @AfterAll static void close() { if (factory != null) factory.destroy(); }
    @BeforeEach void reset() {
        tx.executeWithoutResult(status -> repository.deleteAll());
        outbox = new OrderEventOutbox(repository, new ObjectMapper().registerModule(new JavaTimeModule()),
                "order-lifecycle", "orders");
    }
    Order order() {
        return new Order(new Account("ACC-0001", "Jay", "jay@example.com", null, BigDecimal.TEN, AccountStatus.ACTIVE),
                new Instrument("AAPL", "Apple", "EQUITY", "USD", true), 1, BigDecimal.ONE, OrderSide.BUY, "key");
    }
    @Test void rolledBackStateChangeCannotLeaveAPublishableEvent() {
        assertThrows(IllegalStateException.class, () -> tx.executeWithoutResult(status -> {
            outbox.lifecycle(order(), EventType.ORDER_ACCEPTED, null);
            assertEquals(1, repository.count());
            throw new IllegalStateException("Order transaction failed");
        }));
        assertEquals(0L, tx.execute(status -> repository.count()).longValue());
        tx.executeWithoutResult(status -> new OrderOutboxRelay(repository, kafka).publishPending());
        verifyNoInteractions(kafka);
    }
    @Test void committedEventsAreMarkedOnlyAfterTheirKafkaAcknowledgementsCommit() {
        var order = order();
        tx.executeWithoutResult(status -> {
            outbox.lifecycle(order, EventType.ORDER_ACCEPTED, null);
            outbox.command(order, "SUBMIT");
        });
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(null));
        tx.executeWithoutResult(status -> new OrderOutboxRelay(repository, kafka).publishPending());
        assertEquals(2L, tx.execute(status -> repository.findAll().stream().filter(e -> e.getPublishedAt() != null).count()).longValue());
        tx.executeWithoutResult(status -> new OrderOutboxRelay(repository, kafka).publishPending());
        verify(kafka, times(2)).send(anyString(), anyString(), anyString());
    }
    @Test void failedDeliveryRemainsInTheDatabaseForTheNextRelayInstance() {
        tx.executeWithoutResult(status -> outbox.lifecycle(order(), EventType.ORDER_CANCELLED, "User cancelled"));
        when(kafka.send(anyString(), anyString(), anyString()))
            .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("Kafka offline")))
            .thenReturn(CompletableFuture.completedFuture(null));
        tx.executeWithoutResult(status -> new OrderOutboxRelay(repository, kafka).publishPending());
        assertEquals(1L, tx.execute(status -> repository.findAll().stream().filter(e -> e.getPublishedAt() == null).count()).longValue());
        tx.executeWithoutResult(status -> new OrderOutboxRelay(repository, kafka).publishPending());
        assertEquals(0L, tx.execute(status -> repository.findAll().stream().filter(e -> e.getPublishedAt() == null).count()).longValue());
    }
}

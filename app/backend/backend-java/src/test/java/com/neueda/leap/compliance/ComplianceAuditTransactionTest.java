package com.neueda.leap.compliance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neueda.leap.enums.AccountStatus;
import com.neueda.leap.enums.OrderSide;
import com.neueda.leap.models.Account;
import com.neueda.leap.models.ComplianceCheck;
import com.neueda.leap.models.Instrument;
import com.neueda.leap.repositories.ComplianceCheckRepository;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Real transactions: a blocked order rolls back, but its audit rows must survive. The link to an
 * accepted order must roll back with that order.
 */
@SpringJUnitConfig(ComplianceAuditTransactionTest.Config.class)
class ComplianceAuditTransactionTest {

    @Configuration
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = ComplianceCheckRepository.class,
            includeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = ComplianceCheckRepository.class))
    static class Config {
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory() {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(new DriverManagerDataSource("jdbc:h2:mem:compliance;DB_CLOSE_DELAY=-1", "sa", ""));
            factory.setManagedTypes(PersistenceManagedTypes.of(ComplianceCheck.class.getName()));
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "create-drop"));
            return factory;
        }
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory emf) { return new JpaTransactionManager(emf); }
        @Bean TransactionTemplate transactionTemplate(PlatformTransactionManager tm) { return new TransactionTemplate(tm); }
        @Bean ComplianceAuditRecorder recorder(ComplianceCheckRepository checks) { return new ComplianceAuditRecorder(checks, new ObjectMapper()); }
    }

    @Autowired ComplianceAuditRecorder recorder;
    @Autowired ComplianceCheckRepository checks;
    @Autowired TransactionTemplate tx;

    final OrderContext order = new OrderContext(
            new Account("ACC-0002", "Brian", "b@example.com", null, new BigDecimal("5000.00"), AccountStatus.ACTIVE),
            new Instrument("TSLA", "Tesla", "EQUITY", "USD", true), OrderSide.BUY, 2, new BigDecimal("200.00"),
            0, List.of(), List.of(), symbol -> Optional.empty());

    final ComplianceReport report = new ComplianceReport(UUID.randomUUID(), List.of(
            RuleResult.block("RESTRICTED_LIST", "You can't buy TSLA. It's on the restricted list: under review",
                    Map.of("restrictedReason", "under review")),
            RuleResult.pass("CASH_AVAILABLE"),
            RuleResult.warn("SINGLE_STOCK_LIMIT", "Concentrated", Map.of())));

    @BeforeEach void reset() {
        tx.executeWithoutResult(status -> checks.deleteAll());
    }

    @Test void blockedOrderKeepsItsAuditRowsAfterTheOrderTransactionRollsBack() {
        assertThrows(IllegalStateException.class, () -> tx.executeWithoutResult(status -> {
            recorder.record(report, order, Set.of("SINGLE_STOCK_LIMIT"));
            throw new IllegalStateException("order blocked");
        }));

        List<ComplianceCheck> rows = checks.findByCheckGroupIdOrderById(report.checkGroupId());
        assertEquals(List.of("RESTRICTED_LIST", "CASH_AVAILABLE", "SINGLE_STOCK_LIMIT"),
                rows.stream().map(ComplianceCheck::getRuleId).toList());
        ComplianceCheck block = rows.get(0);
        assertEquals(ComplianceOutcome.BLOCK, block.getOutcome());
        assertEquals("ACC-0002", block.getAccountId());
        assertEquals("TSLA", block.getSymbol());
        assertEquals(new BigDecimal("200.00"), block.getPrice());
        assertEquals("{\"restrictedReason\":\"under review\"}", block.getDetails());
        assertNull(block.getOrderId());
        assertTrue(rows.get(2).isAcknowledged(), "the acknowledged warning is marked");
        assertFalse(rows.get(1).isAcknowledged(), "passes are never marked acknowledged");
    }

    @Test void acceptedOrderIsLinkedToItsChecks() {
        UUID orderId = UUID.randomUUID();
        tx.executeWithoutResult(status -> {
            recorder.record(report, order, Set.of());
            recorder.linkOrder(report.checkGroupId(), orderId);
        });
        assertEquals(3, checks.findByOrderIdOrderById(orderId).size());
    }

    @Test void linkRollsBackWithTheOrderButTheChecksStay() {
        UUID orderId = UUID.randomUUID();
        assertThrows(IllegalStateException.class, () -> tx.executeWithoutResult(status -> {
            recorder.record(report, order, Set.of());
            recorder.linkOrder(report.checkGroupId(), orderId);
            throw new IllegalStateException("saving the order failed");
        }));
        assertTrue(checks.findByOrderIdOrderById(orderId).isEmpty());
        assertEquals(3, checks.findByCheckGroupIdOrderById(report.checkGroupId()).size());
    }

    @Test void linkingOutsideAnOrderTransactionIsRefused() {
        assertThrows(org.springframework.transaction.IllegalTransactionStateException.class,
                () -> recorder.linkOrder(report.checkGroupId(), UUID.randomUUID()));
    }
}

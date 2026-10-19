package com.neueda.leap.compliance;

import com.neueda.leap.dtos.ComplianceSubmissionResponse;
import com.neueda.leap.enums.OrderSide;
import com.neueda.leap.models.ComplianceCheck;
import com.neueda.leap.models.Instrument;
import com.neueda.leap.models.RestrictedInstrument;
import com.neueda.leap.repositories.ComplianceCheckRepository;
import com.neueda.leap.repositories.InstrumentsRepository;
import com.neueda.leap.repositories.RestrictedInstrumentRepository;
import com.neueda.leap.services.ComplianceAdminService;
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
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** The admin audit query against a real database: grouping, decision filters, other filters and order */
@SpringJUnitConfig(ComplianceAdminQueryTest.Config.class)
class ComplianceAdminQueryTest {

    @Configuration
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = ComplianceCheckRepository.class,
            includeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = ComplianceCheckRepository.class))
    static class Config {
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory() {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(new DriverManagerDataSource("jdbc:h2:mem:compliance-admin;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", ""));
            factory.setManagedTypes(PersistenceManagedTypes.of(ComplianceCheck.class.getName()));
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "create-drop"));
            return factory;
        }
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory emf) { return new JpaTransactionManager(emf); }
        @Bean TransactionTemplate transactionTemplate(PlatformTransactionManager tm) { return new TransactionTemplate(tm); }
        @Bean RestrictedInstrumentRepository restricted() { return mock(RestrictedInstrumentRepository.class); }
        @Bean InstrumentsRepository instruments() { return mock(InstrumentsRepository.class); }
        @Bean ComplianceAdminService service(ComplianceCheckRepository checks, RestrictedInstrumentRepository restricted,
                                             InstrumentsRepository instruments) {
            return new ComplianceAdminService(checks, restricted, instruments);
        }
    }

    @Autowired ComplianceAdminService service;
    @Autowired ComplianceCheckRepository checks;
    @Autowired RestrictedInstrumentRepository restricted;
    @Autowired InstrumentsRepository instruments;
    @Autowired TransactionTemplate tx;

    UUID blocked;
    UUID waiting;
    UUID acknowledged;
    UUID clean;

    /** Saves one submission: the given rules, all at the same time */
    UUID submission(String account, String symbol, Object[]... rules) {
        UUID group = UUID.randomUUID();
        tx.executeWithoutResult(status -> {
            for (Object[] rule : rules) {
                checks.save(new ComplianceCheck(group, account, symbol, OrderSide.BUY, 1, new BigDecimal("100.00"),
                        (String) rule[0], (ComplianceOutcome) rule[1], rule[1] == ComplianceOutcome.PASS ? null : rule[0] + " reason",
                        null, (boolean) rule[2], LocalDateTime.now()));
            }
        });
        return group;
    }

    static Object[] rule(String id, ComplianceOutcome outcome, boolean acknowledged) {
        return new Object[] {id, outcome, acknowledged};
    }

    @BeforeEach void data() {
        reset(restricted, instruments);
        tx.executeWithoutResult(status -> checks.deleteAll());
        clean = submission("ACC-0001", "AAPL", rule("RESTRICTED_LIST", ComplianceOutcome.PASS, false), rule("CASH_AVAILABLE", ComplianceOutcome.PASS, false));
        blocked = submission("ACC-0002", "TSLA", rule("RESTRICTED_LIST", ComplianceOutcome.BLOCK, false),
                rule("SINGLE_STOCK_LIMIT", ComplianceOutcome.WARN, true));
        waiting = submission("ACC-0002", "NVDA", rule("RESTRICTED_LIST", ComplianceOutcome.PASS, false),
                rule("SINGLE_STOCK_LIMIT", ComplianceOutcome.WARN, false));
        acknowledged = submission("ACC-0002", "NVDA", rule("RESTRICTED_LIST", ComplianceOutcome.PASS, false),
                rule("SINGLE_STOCK_LIMIT", ComplianceOutcome.WARN, true));
    }

    List<UUID> ids(List<ComplianceSubmissionResponse> submissions) {
        return submissions.stream().map(ComplianceSubmissionResponse::checkGroupId).toList();
    }

    @Test void newestSubmissionsFirstWithEveryRuleResult() {
        List<ComplianceSubmissionResponse> all = service.submissions(null, null, null, 50);
        assertEquals(List.of(acknowledged, waiting, blocked, clean), ids(all));
        ComplianceSubmissionResponse tsla = all.get(2);
        assertEquals("TSLA", tsla.symbol());
        assertEquals(ComplianceDecision.BLOCKED, tsla.decision());
        assertEquals(List.of("RESTRICTED_LIST", "SINGLE_STOCK_LIMIT"), tsla.results().stream().map(ComplianceSubmissionResponse.Result::rule).toList());
        assertEquals("RESTRICTED_LIST reason", tsla.results().get(0).reason());
    }

    @Test void filtersByDecision() {
        assertEquals(List.of(blocked), ids(service.submissions(ComplianceDecision.BLOCKED, null, null, 50)));
        assertEquals(List.of(waiting), ids(service.submissions(ComplianceDecision.NEEDS_ACKNOWLEDGEMENT, null, null, 50)));
        assertEquals(List.of(acknowledged, clean), ids(service.submissions(ComplianceDecision.ALLOWED, null, null, 50)));
    }

    @Test void filtersByAccountAndSymbolIgnoringSymbolCase() {
        assertEquals(List.of(clean), ids(service.submissions(null, "ACC-0001", null, 50)));
        assertEquals(List.of(acknowledged, waiting), ids(service.submissions(null, "ACC-0002", "nvda", 50)));
        assertTrue(service.submissions(null, "ACC-9999", "", 50).isEmpty());
    }

    @Test void limitKeepsWholeSubmissions() {
        List<ComplianceSubmissionResponse> two = service.submissions(null, null, null, 2);
        assertEquals(List.of(acknowledged, waiting), ids(two));
        assertEquals(2, two.get(0).results().size(), "a submission is never cut in half by the limit");
    }

    @Test void restrictingAnUnknownSymbolIsNotFound() {
        when(instruments.findBySymbol("NOPE")).thenReturn(Optional.empty());
        var error = assertThrows(ResponseStatusException.class, () -> service.restrict("nope", "review", "admin"));
        assertEquals(404, error.getStatusCode().value());
    }

    @Test void restrictingTwiceIsAConflict() {
        when(instruments.findBySymbol("TSLA")).thenReturn(Optional.of(new Instrument("TSLA", "Tesla Inc.", "Equity", "USD", true)));
        when(restricted.existsById("TSLA")).thenReturn(true);
        var error = assertThrows(ResponseStatusException.class, () -> service.restrict("TSLA", "review", "admin"));
        assertEquals(409, error.getStatusCode().value());
    }

    @Test void restrictionIsSavedInTheAdminsName() {
        when(instruments.findBySymbol("META")).thenReturn(Optional.of(new Instrument("META", "Meta Platforms Inc.", "Equity", "USD", true)));
        when(restricted.save(any())).thenAnswer(call -> call.getArgument(0));
        var saved = service.restrict(" meta ", "  pending corporate action ", "jay");
        assertEquals("META", saved.symbol());
        assertEquals("Meta Platforms Inc.", saved.name());
        assertEquals("pending corporate action", saved.reason());
        assertEquals("jay", saved.addedBy());
    }

    @Test void removingAnUnrestrictedSymbolIsNotFound() {
        var error = assertThrows(ResponseStatusException.class, () -> service.unrestrict("AAPL", "admin"));
        assertEquals(404, error.getStatusCode().value());
        verify(restricted, never()).deleteById(any());
    }

    @Test void restrictedListIsSortedWithNames() {
        when(restricted.findAll()).thenReturn(List.of(
                new RestrictedInstrument("TSLA", "review", "system"), new RestrictedInstrument("META", "action", "jay")));
        when(instruments.findBySymbol("META")).thenReturn(Optional.of(new Instrument("META", "Meta Platforms Inc.", "Equity", "USD", true)));
        when(instruments.findBySymbol("TSLA")).thenReturn(Optional.empty());
        var list = service.restrictedList();
        assertEquals(List.of("META", "TSLA"), list.stream().map(r -> r.symbol()).toList());
        assertEquals("Meta Platforms Inc.", list.get(0).name());
        assertEquals("TSLA", list.get(1).name(), "falls back to the symbol when the instrument is missing");
    }
}

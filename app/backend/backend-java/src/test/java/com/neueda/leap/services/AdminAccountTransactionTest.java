package com.neueda.leap.services;

import com.neueda.leap.dtos.AdminAccountStatusRequest;
import com.neueda.leap.enums.AccountStatus;
import com.neueda.leap.models.Account;
import com.neueda.leap.repositories.AccountRepository;
import com.neueda.leap.repositories.AccountStatusHistoryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;

/** No surrounding test transaction: the service must create its own for the row lock. */
@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:admin-status;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(AdminAccountService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AdminAccountTransactionTest {
    @Autowired private AdminAccountService service;
    @Autowired private AccountRepository accounts;
    @Autowired private AccountStatusHistoryRepository history;

    @Test void suspensionAndReactivationCommitTheirAuditRecords() {
        Account admin = accounts.save(new Account("ACC-TXN1", "txnadmin", "test-password", "ADMIN,USER",
                "admin@example.com", null, AccountStatus.ACTIVE));
        Account user = accounts.save(new Account("ACC-TXN2", "txnuser", "test-password", "USER",
                "user@example.com", null, AccountStatus.ACTIVE));

        service.changeStatus(user.getId(), new AdminAccountStatusRequest(AccountStatus.SUSPENDED, "Account review"), admin.getId());
        assertEquals(AccountStatus.SUSPENDED, accounts.findById(user.getId()).orElseThrow().getStatus());
        var entries = service.history(user.getId());
        assertEquals(1, entries.size());
        assertEquals(AccountStatus.ACTIVE, entries.get(0).previousStatus());
        assertEquals(AccountStatus.SUSPENDED, entries.get(0).newStatus());
        assertEquals("Account review", entries.get(0).reason());
        assertEquals("txnadmin", entries.get(0).administrator());
        assertEquals(1, history.count());

        service.changeStatus(user.getId(), new AdminAccountStatusRequest(AccountStatus.ACTIVE, "Review complete"), admin.getId());
        assertEquals(AccountStatus.ACTIVE, accounts.findById(user.getId()).orElseThrow().getStatus());
        assertEquals(2, service.history(user.getId()).size());
    }

    @Test void auditFailureRollsBackTheStatusChange() {
        Account user = accounts.save(new Account("ACC-TXN3", "rollbackuser", "test-password", "USER",
                "rollback@example.com", null, AccountStatus.ACTIVE));
        assertThrows(java.util.NoSuchElementException.class, () -> service.changeStatus(user.getId(),
                new AdminAccountStatusRequest(AccountStatus.SUSPENDED, "Account review"), -1L));
        assertEquals(AccountStatus.ACTIVE, accounts.findById(user.getId()).orElseThrow().getStatus());
        assertTrue(service.history(user.getId()).isEmpty());
    }
}

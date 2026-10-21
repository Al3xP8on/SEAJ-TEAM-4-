package com.neueda.leap.services;

import com.neueda.leap.enums.AccountStatus;
import com.neueda.leap.enums.RiskProfile;
import com.neueda.leap.models.Account;
import com.neueda.leap.repositories.AccountRepository;
import com.neueda.leap.repositories.ComplianceCheckRepository;
import com.neueda.leap.repositories.InstrumentsRepository;
import com.neueda.leap.repositories.RestrictedInstrumentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Admins set the risk profile the compliance limits come from */
class ComplianceRiskProfileTest {
    final AccountRepository accounts = mock(AccountRepository.class);
    final ComplianceAdminService service = new ComplianceAdminService(mock(ComplianceCheckRepository.class),
            mock(RestrictedInstrumentRepository.class), mock(InstrumentsRepository.class), accounts);

    Account account(long id, String name, AccountStatus status) {
        Account account = new Account("ACC-000" + id, name, name + "@example.com", null, BigDecimal.ZERO, status);
        ReflectionTestUtils.setField(account, "id", id);
        return account;
    }

    @Test void listsAccountsByNameWithBalancedAsTheDefault() {
        Account zoe = account(1, "zoe", AccountStatus.ACTIVE);
        Account brian = account(2, "Brian", AccountStatus.ACTIVE);
        brian.setRiskProfile(RiskProfile.CONSERVATIVE);
        when(accounts.findAll()).thenReturn(List.of(zoe, brian));

        var list = service.riskProfiles();
        assertEquals(List.of("Brian", "zoe"), list.stream().map(a -> a.holderName()).toList());
        assertEquals(RiskProfile.CONSERVATIVE, list.get(0).riskProfile());
        assertEquals(RiskProfile.BALANCED, list.get(1).riskProfile());
    }

    @Test void changesTheProfileUnderTheAccountLock() {
        Account brian = account(2, "Brian", AccountStatus.ACTIVE);
        when(accounts.findByIdForUpdate(2L)).thenReturn(Optional.of(brian));

        var updated = service.setRiskProfile(2L, RiskProfile.CONSERVATIVE, "jay");
        assertEquals(RiskProfile.CONSERVATIVE, updated.riskProfile());
        assertEquals(RiskProfile.CONSERVATIVE, brian.getRiskProfile());
        verify(accounts).save(brian);
        verify(accounts, never()).findById(any());
    }

    @Test void suspendedAccountsCanStillBeChanged() {
        when(accounts.findByIdForUpdate(2L)).thenReturn(Optional.of(account(2, "Brian", AccountStatus.SUSPENDED)));
        assertEquals(RiskProfile.AGGRESSIVE, service.setRiskProfile(2L, RiskProfile.AGGRESSIVE, "jay").riskProfile());
    }

    @Test void closedAccountsCantBeChanged() {
        when(accounts.findByIdForUpdate(3L)).thenReturn(Optional.of(account(3, "Carla", AccountStatus.CLOSED)));
        var error = assertThrows(ResponseStatusException.class, () -> service.setRiskProfile(3L, RiskProfile.AGGRESSIVE, "jay"));
        assertEquals(409, error.getStatusCode().value());
        verify(accounts, never()).save(any());
    }

    @Test void unknownAccountIsNotFound() {
        when(accounts.findByIdForUpdate(99L)).thenReturn(Optional.empty());
        var error = assertThrows(ResponseStatusException.class, () -> service.setRiskProfile(99L, RiskProfile.BALANCED, "jay"));
        assertEquals(404, error.getStatusCode().value());
    }
}

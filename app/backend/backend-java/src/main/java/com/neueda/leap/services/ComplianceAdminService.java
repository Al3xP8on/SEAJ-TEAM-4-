package com.neueda.leap.services;

import com.neueda.leap.compliance.ComplianceDecision;
import com.neueda.leap.compliance.ComplianceOutcome;
import com.neueda.leap.dtos.AccountRiskProfileResponse;
import com.neueda.leap.dtos.ComplianceSubmissionResponse;
import com.neueda.leap.dtos.RestrictedInstrumentResponse;
import com.neueda.leap.enums.AccountStatus;
import com.neueda.leap.enums.RiskProfile;
import com.neueda.leap.models.Account;
import com.neueda.leap.models.ComplianceCheck;
import com.neueda.leap.models.Instrument;
import com.neueda.leap.models.RestrictedInstrument;
import com.neueda.leap.repositories.AccountRepository;
import com.neueda.leap.repositories.ComplianceCheckRepository;
import com.neueda.leap.repositories.InstrumentsRepository;
import com.neueda.leap.repositories.RestrictedInstrumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** The admin view of pre-trade compliance: the audit trail, and the restricted list */
@Service
@Transactional(readOnly = true)
public class ComplianceAdminService {
    private static final Logger logger = LoggerFactory.getLogger(ComplianceAdminService.class);
    static final int MAX_SUBMISSIONS = 200;

    private final ComplianceCheckRepository checks;
    private final RestrictedInstrumentRepository restricted;
    private final InstrumentsRepository instruments;
    private final AccountRepository accounts;

    public ComplianceAdminService(ComplianceCheckRepository checks, RestrictedInstrumentRepository restricted,
                                  InstrumentsRepository instruments, AccountRepository accounts) {
        this.checks = checks;
        this.restricted = restricted;
        this.instruments = instruments;
        this.accounts = accounts;
    }

    /** The newest submissions first, each with every rule's result */
    public List<ComplianceSubmissionResponse> submissions(ComplianceDecision decision, String accountId, String symbol, int limit) {
        List<UUID> groups = checks.recentGroups(clean(accountId), clean(symbol).toUpperCase(Locale.ROOT),
                decision == null ? "" : decision.name(), PageRequest.of(0, Math.max(1, Math.min(limit, MAX_SUBMISSIONS))));
        if (groups.isEmpty()) return List.of();

        Map<UUID, List<ComplianceCheck>> rows = new LinkedHashMap<>();
        groups.forEach(group -> rows.put(group, new java.util.ArrayList<>()));
        checks.findByCheckGroupIdInOrderById(groups).forEach(row -> rows.get(row.getCheckGroupId()).add(row));
        return rows.values().stream().filter(group -> !group.isEmpty()).map(ComplianceAdminService::toResponse).toList();
    }

    public List<RestrictedInstrumentResponse> restrictedList() {
        return restricted.findAll().stream()
                .sorted(Comparator.comparing(RestrictedInstrument::getSymbol))
                .map(entry -> RestrictedInstrumentResponse.from(entry, instrumentName(entry.getSymbol())))
                .toList();
    }

    @Transactional
    public RestrictedInstrumentResponse restrict(String symbol, String reason, String adminUsername) {
        String normalized = clean(symbol).toUpperCase(Locale.ROOT);
        Instrument instrument = instruments.findBySymbol(normalized)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No instrument with symbol " + normalized));
        if (restricted.existsById(normalized)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, normalized + " is already on the restricted list.");
        }
        RestrictedInstrument entry = restricted.save(new RestrictedInstrument(normalized, reason.trim(), adminUsername));
        logger.info("Restricted list: {} added by {}", normalized, adminUsername);
        return RestrictedInstrumentResponse.from(entry, instrument.getName());
    }

    @Transactional
    public void unrestrict(String symbol, String adminUsername) {
        String normalized = clean(symbol).toUpperCase(Locale.ROOT);
        if (!restricted.existsById(normalized)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, normalized + " is not on the restricted list.");
        }
        restricted.deleteById(normalized);
        logger.info("Restricted list: {} removed by {}", normalized, adminUsername);
    }

    /** Every account and its risk profile, by holder name */
    public List<AccountRiskProfileResponse> riskProfiles() {
        return accounts.findAll().stream()
                .sorted(Comparator.comparing(Account::getName, String.CASE_INSENSITIVE_ORDER))
                .map(AccountRiskProfileResponse::from)
                .toList();
    }

    /**
     * Changes the limits the compliance rules apply to an account, from its next order. Holds the
     * account row lock used by order placement, so an order in flight sees either the old or new profile.
     */
    @Transactional
    public AccountRiskProfileResponse setRiskProfile(Long id, RiskProfile profile, String adminUsername) {
        Account account = accounts.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
        if (account.getStatus() == AccountStatus.CLOSED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Closed accounts can't be changed.");
        }
        RiskProfile previous = account.getRiskProfile();
        account.setRiskProfile(profile);
        accounts.save(account);
        logger.info("Risk profile for account {} changed from {} to {} by {}",
                com.neueda.leap.utils.LogMaskingUtil.maskAccountId(account.getAccountId()), previous, profile, adminUsername);
        return AccountRiskProfileResponse.from(account);
    }

    private String instrumentName(String symbol) {
        return instruments.findBySymbol(symbol).map(Instrument::getName).orElse(symbol);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static ComplianceSubmissionResponse toResponse(List<ComplianceCheck> group) {
        ComplianceCheck first = group.get(0);
        boolean blocked = group.stream().anyMatch(c -> c.getOutcome() == ComplianceOutcome.BLOCK);
        boolean waiting = group.stream().anyMatch(c -> c.getOutcome() == ComplianceOutcome.WARN && !c.isAcknowledged());
        ComplianceDecision decision = blocked ? ComplianceDecision.BLOCKED
                : waiting ? ComplianceDecision.NEEDS_ACKNOWLEDGEMENT : ComplianceDecision.ALLOWED;
        return new ComplianceSubmissionResponse(first.getCheckGroupId(), first.getCheckedAt(), first.getAccountId(),
                first.getSymbol(), first.getSide(), first.getQuantity(), first.getPrice(), decision, first.getOrderId(),
                group.stream().map(c -> new ComplianceSubmissionResponse.Result(
                        c.getRuleId(), c.getOutcome(), c.getReason(), c.isAcknowledged())).toList());
    }
}

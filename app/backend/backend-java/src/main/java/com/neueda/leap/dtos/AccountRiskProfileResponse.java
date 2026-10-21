package com.neueda.leap.dtos;

import com.neueda.leap.enums.AccountStatus;
import com.neueda.leap.enums.RiskProfile;
import com.neueda.leap.models.Account;

/** An account and the risk profile its compliance limits come from */
public record AccountRiskProfileResponse(Long id, String accountId, String holderName, String username,
                                         AccountStatus status, RiskProfile riskProfile) {
    public static AccountRiskProfileResponse from(Account account) {
        return new AccountRiskProfileResponse(account.getId(), account.getAccountId(), account.getName(),
                account.getUsername(), account.getStatus(), account.getRiskProfile());
    }
}

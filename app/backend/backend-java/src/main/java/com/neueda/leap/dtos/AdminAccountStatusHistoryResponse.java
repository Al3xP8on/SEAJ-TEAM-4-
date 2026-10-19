package com.neueda.leap.dtos;

import com.neueda.leap.enums.AccountStatus;
import com.neueda.leap.models.AccountStatusHistory;
import java.time.LocalDateTime;

public record AdminAccountStatusHistoryResponse(Long id, AccountStatus previousStatus,
        AccountStatus newStatus, String reason, String administrator, LocalDateTime changedOn) {
    public static AdminAccountStatusHistoryResponse from(AccountStatusHistory history) {
        return new AdminAccountStatusHistoryResponse(history.getId(), history.getPreviousStatus(),
                history.getNewStatus(), history.getReason(), history.getAdministrator().getUsername(),
                history.getChangedOn());
    }
}
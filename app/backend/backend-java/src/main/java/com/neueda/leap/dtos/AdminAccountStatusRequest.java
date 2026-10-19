package com.neueda.leap.dtos;

import com.neueda.leap.enums.AccountStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdminAccountStatusRequest(@NotNull AccountStatus status, @Size(max = 500) String reason) {
	public boolean requiresReason() {
		return status == AccountStatus.SUSPENDED || status == AccountStatus.CLOSED;
	}

	public String normalizedReason() {
		return reason == null ? "" : reason.trim();
	}
}

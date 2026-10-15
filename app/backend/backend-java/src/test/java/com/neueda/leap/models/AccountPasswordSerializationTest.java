// Verifies that serialized account responses never expose credential fields or password values.
// Protects the password-hiding behavior when accounts are converted to JSON.
package com.neueda.leap.models;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neueda.leap.enums.AccountStatus;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AccountPasswordSerializationTest {
    @Test
    void accountJsonNeverContainsPasswordOrHash() throws Exception {
        Account account = new Account("ACC-001", "alice", "SecurePass123!", "Alice", "alice@example.com", null,
                BigDecimal.ZERO, AccountStatus.ACTIVE);
        String json = new ObjectMapper().findAndRegisterModules().writeValueAsString(account);
        assertFalse(json.contains("passwordHash"));
        assertFalse(json.contains("SecurePass123!"));
        assertFalse(json.contains("$2"));
    }
}

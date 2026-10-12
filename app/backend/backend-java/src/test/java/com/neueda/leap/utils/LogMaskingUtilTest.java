package com.neueda.leap.utils;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;

class LogMaskingUtilTest {
    @Test void masksUuidMiddleAndPreservesLength() {
        UUID uuid = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        String expected = "550e8400" + "*".repeat(24) + "0000";
        assertEquals(expected, LogMaskingUtil.maskUUID(uuid));
        assertEquals(expected, LogMaskingUtil.maskUUID(uuid.toString()));
        assertEquals(expected, LogMaskingUtil.maskId(uuid));
        assertEquals(expected, LogMaskingUtil.maskId(uuid.toString()));
    }
    @Test void handlesNullEmptyAndBoundaryLengthUuids() {
        assertEquals("[null]", LogMaskingUtil.maskUUID((UUID) null));
        assertEquals("[null]", LogMaskingUtil.maskUUID((String) null));
        assertEquals("[null]", LogMaskingUtil.maskUUID(""));
        assertEquals("123456789012", LogMaskingUtil.maskUUID("123456789012"));
        assertEquals("12345678*0123", LogMaskingUtil.maskUUID("1234567890123"));
        assertEquals("[null]", LogMaskingUtil.maskId((UUID) null));
        assertEquals("[null]", LogMaskingUtil.maskId((String) null));
    }
    @Test void masksAccountIdWithFourCharacterPrefixAndThreeCharacterSuffix() {
        assertEquals("[null]", LogMaskingUtil.maskAccountId(null));
        assertEquals("[null]", LogMaskingUtil.maskAccountId(""));
        assertEquals("1234567", LogMaskingUtil.maskAccountId("1234567"));
        assertEquals("1234*678", LogMaskingUtil.maskAccountId("12345678"));
        assertEquals("ACC-****001", LogMaskingUtil.maskAccountId("ACC-1234001"));
    }
}

package com.neueda.leap.utils;

import java.util.UUID;

/**
 * Utility for masking sensitive identifiers in logs.
 * Follows OWASP security guidelines to prevent sensitive data exposure.
 * 
 * Masks UUIDs and account IDs to show only first 8 and last 4 characters.
 * Example: "550e8400-e29b-41d4-a716-446655440000" → "550e8400-****-****-****-44440000"
 */
public class LogMaskingUtil {
    
    private static final String MASK_CHAR = "*";
    private static final int PREFIX_LENGTH = 8;
    private static final int SUFFIX_LENGTH = 4;
    
    /**
     * Masks a UUID string for safe logging.
     * Shows first 8 and last 4 characters.
     * 
     * @param uuid the UUID string to mask
     * @return masked UUID, or "[null]" if input is null
     */
    public static String maskUUID(String uuid) {
        if (uuid == null || uuid.isEmpty()) {
            return "[null]";
        }
        
        if (uuid.length() <= PREFIX_LENGTH + SUFFIX_LENGTH) {
            return uuid;
        }
        
        String prefix = uuid.substring(0, PREFIX_LENGTH);
        String suffix = uuid.substring(uuid.length() - SUFFIX_LENGTH);
        String middle = MASK_CHAR.repeat(uuid.length() - PREFIX_LENGTH - SUFFIX_LENGTH);
        
        return prefix + middle + suffix;
    }
    
    /**
     * Masks a UUID object for safe logging.
     * 
     * @param uuid the UUID object to mask
     * @return masked UUID string, or "[null]" if input is null
     */
    public static String maskUUID(UUID uuid) {
        return maskUUID(uuid == null ? null : uuid.toString());
    }
    
    /**
     * Masks an account ID for safe logging.
     * Shows first 4 and last 3 characters (e.g., "ACC-****-****-0001").
     * 
     * @param accountId the account ID to mask
     * @return masked account ID, or "[null]" if input is null
     */
    public static String maskAccountId(String accountId) {
        if (accountId == null || accountId.isEmpty()) {
            return "[null]";
        }
        
        if (accountId.length() <= 7) {
            return accountId;
        }
        
        String prefix = accountId.substring(0, 4);
        String suffix = accountId.substring(accountId.length() - 3);
        String middle = MASK_CHAR.repeat(accountId.length() - 7);
        
        return prefix + middle + suffix;
    }
    
    /**
     * Masks an order/trade ID for safe logging.
     * Uses UUID masking as these are typically UUID strings.
     * 
     * @param id the ID to mask
     * @return masked ID
     */
    public static String maskId(String id) {
        return maskUUID(id);
    }
    
    /**
     * Masks an order/trade ID for safe logging.
     * Uses UUID masking as these are typically UUID objects.
     * 
     * @param id the UUID to mask
     * @return masked ID
     */
    public static String maskId(UUID id) {
        return maskUUID(id);
    }
}

package com.neueda.leap.utils;

import java.math.BigDecimal;

public class Utils {
    public static BigDecimal calculateTotalValue(BigDecimal price, long quantity) {
        return price.multiply(new BigDecimal(quantity));
    }

    /**
     * Masks a sensitive identifier (like accountId) for safe logging.
     * Keeps the first 4 characters and replaces the rest with asterisks.
     * 
     * Example: "ACC-001" becomes "ACC-***"
     */
    public static String maskSensitiveId(String id) {
        if (id == null || id.isEmpty()) {
            return "***";
        }
        
        if (id.length() <= 4) {
            return "***";
        }
        
        String prefix = id.substring(0, 4);
        return prefix + "-***";
    }
    
    /**
     * Masks a UUID for safe logging.
     * Keeps the first 8 characters and replaces the rest with asterisks.
     * 
     * Example: "550e8400-e29b-41d4-a716-446655440000" becomes "550e8400-****"
     */
    public static String maskUUID(String uuid) {
        if (uuid == null || uuid.isEmpty()) {
            return "****";
        }
        
        if (uuid.length() <= 8) {
            return "****";
        }
        
        String prefix = uuid.substring(0, 8);
        return prefix + "-****";
    }

}

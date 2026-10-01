package com.neueda.leap.enums;

/**
 * Specifies how long an order remains active.
 * 
 * GTC: Good Till Cancel - Order remains active until filled or manually cancelled
 * GTD: Good Till Date - Order remains active until a specified date
 * IOC: Immediate Or Cancel - Order must execute immediately, any unfilled portion is cancelled
 * FOK: Fill Or Kill - Order must execute completely immediately, otherwise cancelled
 */
public enum TimeInForce {
    GTC,    // Good Till Cancel (default)
    GTD,    // Good Till Date
    IOC,    // Immediate Or Cancel
    FOK     // Fill Or Kill
}

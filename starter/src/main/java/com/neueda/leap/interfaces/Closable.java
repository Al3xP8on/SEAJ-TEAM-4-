package com.neueda.leap.interfaces;

import java.math.BigDecimal;

public interface Closable {
    
    /**
     * @throws IllegalStateException if account has remaining balance
     */
 
    void closeAccount();
    void suspendAccount();
    void debit(BigDecimal amount);
    void credit(BigDecimal amount);
    boolean canAfford(BigDecimal amount);
    boolean isValidForTrading();
}

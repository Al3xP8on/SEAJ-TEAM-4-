package com.neueda.leap.interfaces;

/**
 * Interface Segregation Principle: Clients that need account closure operations
 * only depend on what they need.
 */
public interface Closable {
    /**
     * Close the account
     * @throws IllegalStateException if account has remaining balance
     */
    void closeAccount();

    /**
     * Suspend the account temporarily
     */
    void suspendAccount();
}

package com.neueda.leap;

import java.math.BigDecimal;
import com.neueda.enums.AccountStatus;

public class Main {
    public static void main(String[] args) {
        try {
            System.out.println("=== SEAJ Trading Application Started ===\n");
            
            // Create and test Account with BigDecimal for currency
            Account account = new Account("ACC001", "John Doe", new BigDecimal("10000.00"), AccountStatus.ACTIVE);
            System.out.println("Created Account: " + account);
            
            // Test Account Operations
            System.out.println("\n--- Account Operations ---");
            account.deposit(new BigDecimal("5000.00"));
            System.out.println("After deposit: Balance = " + account.getCashBalance());
            
            account.withdraw(new BigDecimal("2000.00"));
            System.out.println("After withdrawal: Balance = " + account.getCashBalance());
            
            // Test canAfford operation
            System.out.println("\nCan afford 5000? " + account.canAfford(new BigDecimal("5000.00")));
            System.out.println("Is valid for trading? " + account.isValidForTrading());
            
            // Create and test Instrument
            System.out.println("\n--- Instrument Operations ---");
            Instrument instrument = new Instrument("AAPL", "Apple Inc.", "Equity", "USD", true);
            System.out.println("Created Instrument: " + instrument);
            
            System.out.println("\n=== Application Running Successfully ===");
            
        } catch (Exception e) {
            System.err.println("Application Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}

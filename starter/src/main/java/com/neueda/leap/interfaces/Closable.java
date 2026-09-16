package com.neueda.leap.interfaces;

public interface Closable {
    
    @throws IllegalStateException if account has remaining balance
 
    void closeAccount();

 
    void suspendAccount();
}

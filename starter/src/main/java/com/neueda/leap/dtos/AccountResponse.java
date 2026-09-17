package com.neueda.leap.dtos;

import com.neueda.leap.enums.AccountStatus;
import java.math.BigDecimal;

public class AccountResponse {
    private String accountId;
    private String holderName;
    private BigDecimal cashBalance;
    private AccountStatus status;

    public AccountResponse(String accountId, String holderName, BigDecimal cashBalance, AccountStatus status){
        this.accountId = accountId;
        this.holderName = holderName;
        this.cashBalance = cashBalance;
        this.status = status;
    }

    public String getAccountId(){
        return this.accountId;
    }

    public String getHolderName(){
        return this.holderName;
    }

    public BigDecimal getCashBalance(){
        return this.cashBalance;
    }

    public AccountStatus getStatus(){
        return this.status;
    }

}

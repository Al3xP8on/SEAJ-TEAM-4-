package com.neueda.leap.dtos;

public class LoginResponse {
    private String token;
    private String username;
    private String accountId;
    private Long expiresIn;

    public LoginResponse() {
    }

    public LoginResponse(String token, String username, String accountId, Long expiresIn) {
        this.token = token;
        this.username = username;
        this.accountId = accountId;
        this.expiresIn = expiresIn;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public Long getExpiresIn() {
        return expiresIn;
    }

    public void setExpiresIn(Long expiresIn) {
        this.expiresIn = expiresIn;
    }
}

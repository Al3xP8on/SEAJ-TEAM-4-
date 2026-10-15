// Defines the username/password request used to start the email-verified login flow.
// Validates required credentials and their lengths before forwarding them to the authentication service.
package com.neueda.leap.dtos;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class LoginRequest {
    @NotBlank @Size(min = 3, max = 100)
    private String username;
    @NotNull @Size(min = 1, max = 72)
    private String password;

    public LoginRequest() {
    }

    public LoginRequest(String username, String password) {
        this.username = username;
        this.password = password;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}

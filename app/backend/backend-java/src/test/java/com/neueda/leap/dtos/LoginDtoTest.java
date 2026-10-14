package com.neueda.leap.dtos;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LoginDtoTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void deserializesLoginCredentialsFromAnHttpRequestBody() throws Exception {
        LoginRequest request = mapper.readValue(
                "{\"username\":\"alice\",\"password\":\"example-password\"}", LoginRequest.class);

        assertEquals("alice", request.getUsername());
        assertEquals("example-password", request.getPassword());
    }

    @Test
    void serializesConstructedLoginRequestsForTheAuthenticationService() throws Exception {
        LoginRequest request = new LoginRequest("alice", "example-password");

        JsonNode json = mapper.readTree(mapper.writeValueAsString(request));

        assertEquals("alice", json.get("username").asText());
        assertEquals("example-password", json.get("password").asText());
    }

    @Test
    void roundTripsAllAuthenticationResponseFields() throws Exception {
        LoginResponse original = new LoginResponse("signed.jwt.token", "alice", "ACC-001", 1800L);
        String json = mapper.writeValueAsString(original);
        LoginResponse response = mapper.readValue(json, LoginResponse.class);

        assertEquals("signed.jwt.token", response.getToken());
        assertEquals("alice", response.getUsername());
        assertEquals("ACC-001", response.getAccountId());
        assertEquals(1800L, response.getExpiresIn());
    }

    @Test
    void leavesMissingOptionalResponseFieldsUnsetWhenDeserializing() throws Exception {
        LoginResponse response = mapper.readValue("{\"token\":\"signed.jwt.token\"}", LoginResponse.class);

        assertEquals("signed.jwt.token", response.getToken());
        assertNull(response.getUsername());
        assertNull(response.getAccountId());
        assertNull(response.getExpiresIn());
    }
}

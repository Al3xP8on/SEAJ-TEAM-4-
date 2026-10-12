package com.neueda.leap.security;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

class SecurityConfigTest {
    private static final String SECRET = "test-only-secret-with-at-least-32-bytes";
    private SecurityConfig config() {
        SecurityConfig config = new SecurityConfig();
        ReflectionTestUtils.setField(config, "sharedSecret", SECRET);
        return config;
    }
    private String token(String secret, Instant expiresAt) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("alice")
                .claim("roles", List.of("TRADER"))
                .expirationTime(Date.from(expiresAt))
                .build();
        SignedJWT token = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        token.sign(new MACSigner(secret.getBytes(StandardCharsets.UTF_8)));
        return token.serialize();
    }
    @Test void validatesSignedTokenAndPreservesSubjectAndRoleClaims() throws Exception {
        var jwt = config().jwtDecoder().decode(token(SECRET, Instant.now().plusSeconds(3600)));
        assertEquals("alice", jwt.getSubject());
        assertEquals(List.of("TRADER"), jwt.getClaimAsStringList("roles"));
    }
    @Test void rejectsTokenSignedWithDifferentSecret() throws Exception {
        String token = token("different-test-only-secret-at-least-32-bytes", Instant.now().plusSeconds(3600));
        assertThrows(JwtException.class, () -> config().jwtDecoder().decode(token));
    }
    @Test void rejectsExpiredAndMalformedTokens() throws Exception {
        String expired = token(SECRET, Instant.now().minusSeconds(3600));
        assertThrows(JwtException.class, () -> config().jwtDecoder().decode(expired));
        assertThrows(JwtException.class, () -> config().jwtDecoder().decode("not-a-jwt"));
    }
}

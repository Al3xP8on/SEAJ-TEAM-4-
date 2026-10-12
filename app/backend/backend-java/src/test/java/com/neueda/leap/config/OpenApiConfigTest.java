package com.neueda.leap.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenApiConfigTest {
    private final OpenApiConfig config = new OpenApiConfig();

    @Test
    void documentsJwtBearerAuthenticationAsTheDefaultSecurityRequirement() {
        OpenAPI api = config.openAPI();
        SecurityScheme bearer = api.getComponents().getSecuritySchemes().get("bearerAuth");

        assertNotNull(bearer);
        assertEquals("bearerAuth", bearer.getName());
        assertEquals(SecurityScheme.Type.HTTP, bearer.getType());
        assertEquals("bearer", bearer.getScheme());
        assertEquals("JWT", bearer.getBearerFormat());
        assertEquals(1, api.getSecurity().size());
        assertEquals(List.of(), api.getSecurity().get(0).get("bearerAuth"));
    }

    @Test
    void createsAnHttpClientUsingTheSuppliedBuilderCustomizations() {
        RestTemplateBuilder builder = new RestTemplateBuilder()
                .defaultHeader("X-Application", "seaj")
                .additionalInterceptors((request, body, execution) -> {
                    request.getHeaders().add("X-Test-Customizer", "applied");
                    return execution.execute(request, body);
                });
        RestTemplate client = config.restTemplate(builder);
        MockRestServiceServer server = MockRestServiceServer.bindTo(client).build();
        server.expect(requestTo("https://auth.example.test/login"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Application", "seaj"))
                .andExpect(header("X-Test-Customizer", "applied"))
                .andRespond(withSuccess("{\"token\":\"jwt\"}", MediaType.APPLICATION_JSON));

        String response = client.postForObject("https://auth.example.test/login", "request", String.class);

        assertEquals("{\"token\":\"jwt\"}", response);
        server.verify();
    }
}

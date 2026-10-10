package com.wisekoin.backend.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.wisekoin.backend.module.auth.jwt.JwtAuthenticationFilter;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

class SecurityConfigTest {
    private final SecurityConfig securityConfig =
            new SecurityConfig(mock(JwtAuthenticationFilter.class));

    @Test
    void authenticationEntryPointReturnsStructuredUnauthorizedJson() throws Exception {
        AuthenticationEntryPoint entryPoint = securityConfig.authenticationEntryPoint();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/wallets");
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(
                request,
                response,
                new InsufficientAuthenticationException("Authentication required"));

        assertEquals(HttpStatus.UNAUTHORIZED.value(), response.getStatus());
        assertTrue(
                MediaType.APPLICATION_JSON.isCompatibleWith(
                        MediaType.parseMediaType(response.getContentType())));
        assertEquals("{\"error\":\"Unauthorized\"}", response.getContentAsString());
    }

    @Test
    void corsConfigurationUsesConfiguredOriginsAndApiMethods() {
        CorsConfigurationSource source =
                securityConfig.corsConfigurationSource(
                        "https://app.example.com, https://admin.example.com");
        CorsConfiguration configuration =
                source.getCorsConfiguration(new MockHttpServletRequest("OPTIONS", "/api/wallets"));

        assertNotNull(configuration);
        assertEquals(
                List.of("https://app.example.com", "https://admin.example.com"),
                configuration.getAllowedOrigins());
        assertEquals(
                List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"),
                configuration.getAllowedMethods());
    }
}

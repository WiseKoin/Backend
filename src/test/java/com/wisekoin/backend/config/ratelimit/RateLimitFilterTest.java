package com.wisekoin.backend.config.ratelimit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wisekoin.backend.module.auth.security.CustomUserDetails;
import com.wisekoin.backend.module.user.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.json.JsonMapper;

class RateLimitFilterTest {
    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void aiEndpointUsesStrictAuthenticatedUserBucketAndReturnsJson429() throws Exception {
        RateLimitService rateLimitService = org.mockito.Mockito.mock(RateLimitService.class);
        RateLimitFilter filter =
                new RateLimitFilter(
                        rateLimitService, JsonMapper.builder().build(), true, 10, 20, 120, 60);
        User user = new User();
        user.setId(7L);
        CustomUserDetails principal = new CustomUserDetails(user);
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                principal, null, principal.getAuthorities()));
        when(rateLimitService.consume("ai:user:7", 10, 60))
                .thenReturn(new RateLimitDecision(false, 10, 0, 120, 12));

        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", "/api/receipts/extract");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(429, response.getStatus());
        assertEquals("12", response.getHeader("Retry-After"));
        assertEquals("10", response.getHeader("X-RateLimit-Limit"));
        assertTrue(response.getContentAsString().contains("Too Many Requests"));
        verify(rateLimitService).consume("ai:user:7", 10, 60);
    }
}

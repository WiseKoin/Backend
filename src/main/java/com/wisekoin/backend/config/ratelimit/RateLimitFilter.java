package com.wisekoin.backend.config.ratelimit;

import com.wisekoin.backend.module.auth.security.CustomUserDetails;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

@Component
public class RateLimitFilter extends OncePerRequestFilter {
    private final RateLimitService rateLimitService;
    private final JsonMapper jsonMapper;
    private final boolean enabled;
    private final int aiRequests;
    private final int authRequests;
    private final int apiRequests;
    private final long windowSeconds;

    public RateLimitFilter(
            RateLimitService rateLimitService,
            JsonMapper jsonMapper,
            @Value("${app.rate-limit.enabled:true}") boolean enabled,
            @Value("${app.rate-limit.ai.requests:10}") int aiRequests,
            @Value("${app.rate-limit.auth.requests:20}") int authRequests,
            @Value("${app.rate-limit.api.requests:120}") int apiRequests,
            @Value("${app.rate-limit.window-seconds:60}") long windowSeconds) {
        this.rateLimitService = rateLimitService;
        this.jsonMapper = jsonMapper;
        if (aiRequests < 1 || authRequests < 1 || apiRequests < 1 || windowSeconds < 1) {
            throw new IllegalArgumentException("Rate limits and window size must be positive");
        }
        this.enabled = enabled;
        this.aiRequests = aiRequests;
        this.authRequests = authRequests;
        this.apiRequests = apiRequests;
        this.windowSeconds = windowSeconds;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !enabled
                || "OPTIONS".equalsIgnoreCase(request.getMethod())
                || !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Policy policy = policyFor(request.getRequestURI());
        String subject = subject(request);
        RateLimitDecision decision =
                rateLimitService.consume(policy.name + ":" + subject, policy.limit, windowSeconds);

        response.setHeader("X-RateLimit-Limit", Integer.toString(decision.limit()));
        response.setHeader("X-RateLimit-Remaining", Integer.toString(decision.remaining()));
        response.setHeader("X-RateLimit-Reset", Long.toString(decision.resetEpochSecond()));

        if (decision.allowed()) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(decision.retryAfterSeconds()));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", HttpStatus.TOO_MANY_REQUESTS.value());
        body.put("error", "Too Many Requests");
        body.put("message", "Rate limit exceeded. Retry after the indicated delay.");
        body.put("path", request.getRequestURI());
        jsonMapper.writeValue(response.getOutputStream(), body);
    }

    private Policy policyFor(String path) {
        if (isPath(path, "/api/receipts") || isPath(path, "/api/voice")) {
            return new Policy("ai", aiRequests);
        }
        if (isPath(path, "/api/auth")) {
            return new Policy("auth", authRequests);
        }
        return new Policy("api", apiRequests);
    }

    private boolean isPath(String requestPath, String basePath) {
        return requestPath.equals(basePath) || requestPath.startsWith(basePath + "/");
    }

    private String subject(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.getPrincipal() instanceof CustomUserDetails user) {
            return "user:" + user.getId();
        }
        return "ip:" + request.getRemoteAddr();
    }

    private record Policy(String name, int limit) {}
}

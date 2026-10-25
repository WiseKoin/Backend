package com.wisekoin.backend.module.ai.suggestion;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wisekoin.backend.module.ai.notification.BankNotificationSuggestionService;
import com.wisekoin.backend.module.ai.notification.NotificationPilotMetricService;
import com.wisekoin.backend.module.ai.suggestion.dto.AiSuggestionRejectionRequest;
import com.wisekoin.backend.module.ai.suggestion.dto.AiSuggestionResponse;
import com.wisekoin.backend.module.auth.security.CustomUserDetails;
import com.wisekoin.backend.module.common.exception.BadRequestException;
import com.wisekoin.backend.module.common.exception.GlobalExceptionHandler;
import com.wisekoin.backend.module.transaction.mapper.TransactionMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

class AiSuggestionControllerTest {
    private AiSuggestionService suggestionService;
    private BankNotificationSuggestionService bankNotificationSuggestionService;
    private NotificationPilotMetricService notificationMetricService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        suggestionService = mock(AiSuggestionService.class);
        bankNotificationSuggestionService = mock(BankNotificationSuggestionService.class);
        notificationMetricService = mock(NotificationPilotMetricService.class);
        mockMvc =
                MockMvcBuilders.standaloneSetup(
                                new AiSuggestionController(
                                        suggestionService,
                                        mock(TransactionMapper.class),
                                        bankNotificationSuggestionService,
                                        notificationMetricService))
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .setCustomArgumentResolvers(authenticatedUser(42L))
                        .build();
    }

    @Test
    void rejectionReturnsLifecycleResponse() throws Exception {
        UUID suggestionId = UUID.randomUUID();
        when(suggestionService.reject(
                        eq(42L),
                        eq(suggestionId),
                        org.mockito.ArgumentMatchers.any(AiSuggestionRejectionRequest.class)))
                .thenReturn(response(suggestionId, "REJECTED", "DUPLICATE"));

        mockMvc.perform(
                        post("/api/ai/suggestions/{id}/rejection", suggestionId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"reason\":\"DUPLICATE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(suggestionId.toString()))
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejection_reason").value("DUPLICATE"));
    }

    @Test
    void emptyClarificationUsesGlobalErrorShape() throws Exception {
        UUID suggestionId = UUID.randomUUID();
        when(suggestionService.clarify(
                        eq(42L), eq(suggestionId), org.mockito.ArgumentMatchers.any()))
                .thenThrow(new BadRequestException("At least one clarification is required"));

        mockMvc.perform(
                        patch("/api/ai/suggestions/{id}/clarification", suggestionId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("At least one clarification is required"));
    }

    @Test
    void rejectionRequiresReason() throws Exception {
        mockMvc.perform(
                        post("/api/ai/suggestions/{id}/rejection", UUID.randomUUID())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").exists());
    }

    @Test
    void notificationSuggestionUsesAuthenticatedUserAndDeidentifiedContract() throws Exception {
        UUID suggestionId = UUID.randomUUID();
        when(bankNotificationSuggestionService.create(eq(42L), org.mockito.ArgumentMatchers.any()))
                .thenReturn(response(suggestionId, "PENDING", null));

        mockMvc.perform(
                        post("/api/ai/suggestions/notifications")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "source": "VCB",
                                          "source_reference_hash": "%s",
                                          "parser_version": "vcb-v1",
                                          "amount": 125000,
                                          "currency": "VND",
                                          "type": "EXPENSE",
                                          "occurred_at": "2026-10-25T09:00:00",
                                          "locale": "vi-VN",
                                          "time_zone": "Asia/Ho_Chi_Minh"
                                        }
                                        """
                                                .formatted("a".repeat(64))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(suggestionId.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    private AiSuggestionResponse response(UUID id, String status, String rejectionReason) {
        Instant now = Instant.parse("2026-10-24T03:00:00Z");
        return new AiSuggestionResponse(
                id,
                "RECEIPT",
                new BigDecimal("125000.00"),
                "VND",
                "EXPENSE",
                "Cua hang mau",
                LocalDateTime.of(2026, 10, 24, 9, 0),
                7L,
                null,
                null,
                false,
                List.of(),
                status,
                rejectionReason,
                null,
                "receipt-v2",
                "model-test",
                "fake",
                null,
                "vi-VN",
                "Asia/Ho_Chi_Minh",
                now,
                now,
                now);
    }

    private HandlerMethodArgumentResolver authenticatedUser(Long userId) {
        CustomUserDetails user = mock(CustomUserDetails.class);
        when(user.getId()).thenReturn(userId);

        return new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.getParameterType() == CustomUserDetails.class;
            }

            @Override
            public Object resolveArgument(
                    MethodParameter parameter,
                    ModelAndViewContainer mavContainer,
                    NativeWebRequest webRequest,
                    WebDataBinderFactory binderFactory) {
                return user;
            }
        };
    }
}

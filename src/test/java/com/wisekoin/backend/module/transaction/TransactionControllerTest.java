package com.wisekoin.backend.module.transaction;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wisekoin.backend.module.auth.security.CustomUserDetails;
import com.wisekoin.backend.module.common.exception.GlobalExceptionHandler;
import com.wisekoin.backend.module.common.exception.IdempotencyConflictException;
import com.wisekoin.backend.module.transaction.dto.TransactionRequest;
import com.wisekoin.backend.module.transaction.dto.TransactionResponse;
import com.wisekoin.backend.module.transaction.mapper.TransactionMapper;
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

class TransactionControllerTest {
    private TransactionService transactionService;
    private TransactionMapper transactionMapper;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        transactionService = mock(TransactionService.class);
        transactionMapper = mock(TransactionMapper.class);
        mockMvc =
                MockMvcBuilders.standaloneSetup(
                                new TransactionController(transactionService, transactionMapper))
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .setCustomArgumentResolvers(authenticatedUser(42L))
                        .build();
    }

    @Test
    void replayIsExposedWithoutChangingResponseBody() throws Exception {
        Transaction transaction = new Transaction();
        transaction.setId(99L);
        when(transactionService.createIdempotently(
                        eq(42L), any(TransactionRequest.class), eq("retry-key")))
                .thenReturn(new TransactionCreationResult(transaction, true));
        when(transactionMapper.toResponse(transaction))
                .thenReturn(TransactionResponse.builder().id(99L).build());

        mockMvc.perform(
                        post("/api/transactions")
                                .header("Idempotency-Key", "retry-key")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "walletId": 1,
                                          "categoryId": 2,
                                          "amount": 1000,
                                          "type": "EXPENSE"
                                        }
                                        """))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotency-Replayed", "true"))
                .andExpect(jsonPath("$.id").value(99));
    }

    @Test
    void conflictingReuseHasStableErrorCode() throws Exception {
        when(transactionService.createIdempotently(
                        eq(42L), any(TransactionRequest.class), eq("reused-key")))
                .thenThrow(new IdempotencyConflictException("Key already used"));

        mockMvc.perform(
                        post("/api/transactions")
                                .header("Idempotency-Key", "reused-key")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "walletId": 1,
                                          "categoryId": 2,
                                          "amount": 1000,
                                          "type": "EXPENSE"
                                        }
                                        """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
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

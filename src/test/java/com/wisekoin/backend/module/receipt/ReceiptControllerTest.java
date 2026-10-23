package com.wisekoin.backend.module.receipt;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wisekoin.backend.module.auth.security.CustomUserDetails;
import com.wisekoin.backend.module.receipt.dto.ReceiptResponse;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.multipart.MultipartFile;

class ReceiptControllerTest {
    private ReceiptService receiptService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        receiptService = mock(ReceiptService.class);
        mockMvc =
                MockMvcBuilders.standaloneSetup(new ReceiptController(receiptService))
                        .setCustomArgumentResolvers(authenticatedUser(42L))
                        .build();
    }

    @Test
    void extractReturnsDecimalAmountAndStableCategoryId() throws Exception {
        when(receiptService.extractReceipt(
                        any(MultipartFile.class),
                        eq(42L),
                        nullable(String.class),
                        nullable(String.class)))
                .thenReturn(
                        new ReceiptResponse(
                                "Neighborhood Market",
                                7L,
                                "Groceries",
                                new BigDecimal("12345.67"),
                                "VND",
                                LocalDateTime.of(2026, 10, 22, 9, 30),
                                new BigDecimal("12000.00"),
                                new BigDecimal("345.67"),
                                BigDecimal.ZERO,
                                BigDecimal.ZERO,
                                "CARD",
                                List.of(),
                                false,
                                List.of(),
                                List.of()));

        MockMultipartFile file =
                new MockMultipartFile("file", "receipt.jpg", "image/jpeg", new byte[] {1});

        mockMvc.perform(multipart("/api/receipts/extract").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.store_name").value("Neighborhood Market"))
                .andExpect(jsonPath("$.category_id").value(7))
                .andExpect(jsonPath("$.category").value("Groceries"))
                .andExpect(jsonPath("$.amount").value(12345.67))
                .andExpect(jsonPath("$.currency").value("VND"))
                .andExpect(jsonPath("$.occurred_at").value("2026-10-22T09:30:00"))
                .andExpect(jsonPath("$.payment_method").value("CARD"))
                .andExpect(jsonPath("$.requires_clarification").value(false));
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

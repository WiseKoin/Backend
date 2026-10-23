package com.wisekoin.backend.module.voice;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wisekoin.backend.module.auth.security.CustomUserDetails;
import com.wisekoin.backend.module.voice.dto.VoiceResponse;
import java.math.BigDecimal;
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

class VoiceControllerTest {
    private VoiceService voiceService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        voiceService = mock(VoiceService.class);
        mockMvc =
                MockMvcBuilders.standaloneSetup(new VoiceController(voiceService))
                        .setCustomArgumentResolvers(authenticatedUser(42L))
                        .build();
    }

    @Test
    void extractReturnsCurrencyUnknownStateAndNullableCategoryId() throws Exception {
        when(voiceService.extractVoice(any(MultipartFile.class), eq(42L)))
                .thenReturn(
                        new VoiceResponse(
                                new BigDecimal("9000.50"),
                                "USD",
                                "UNKNOWN",
                                null,
                                "Unknown",
                                "Coffee with a client",
                                "nine thousand fifty"));

        MockMultipartFile file =
                new MockMultipartFile("file", "voice.m4a", "audio/mp4", new byte[] {1});

        mockMvc.perform(multipart("/api/voice/extract").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(9000.50))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.type").value("UNKNOWN"))
                .andExpect(jsonPath("$.category_id").isEmpty())
                .andExpect(jsonPath("$.category").value("Unknown"));
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

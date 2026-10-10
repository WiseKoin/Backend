package com.wisekoin.backend.module.voice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import com.wisekoin.backend.module.category.CategoryService;
import com.wisekoin.backend.module.voice.dto.VoiceResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

class VoiceServiceTest {
    private VoiceService voiceService;

    @BeforeEach
    void setUp() {
        voiceService =
                new VoiceService(
                        mock(RestClient.class), new JsonMapper(), mock(CategoryService.class));
    }

    @Test
    @DisplayName("Should accept valid audio formats: m4a, mp3, wav, aac, ogg")
    void validateMimeType_WhenValidAudioFormats_ShouldNormalize() {
        MockMultipartFile m4aFile =
                new MockMultipartFile("file", "audio.m4a", "audio/mp4", "fake-audio".getBytes());
        MockMultipartFile mp3File =
                new MockMultipartFile("file", "audio.mp3", "audio/mpeg", "fake-audio".getBytes());
        MockMultipartFile wavFile =
                new MockMultipartFile("file", "audio.wav", "audio/wav", "fake-audio".getBytes());
        MockMultipartFile aacFile =
                new MockMultipartFile("file", "audio.aac", "audio/aac", "fake-audio".getBytes());
        MockMultipartFile oggFile =
                new MockMultipartFile("file", "audio.ogg", "audio/ogg", "fake-audio".getBytes());

        assertEquals("audio/mp4", voiceService.validateAndNormalizeMimeType(m4aFile));
        assertEquals("audio/mp3", voiceService.validateAndNormalizeMimeType(mp3File));
        assertEquals("audio/wav", voiceService.validateAndNormalizeMimeType(wavFile));
        assertEquals("audio/aac", voiceService.validateAndNormalizeMimeType(aacFile));
        assertEquals("audio/ogg", voiceService.validateAndNormalizeMimeType(oggFile));
    }

    @Test
    @DisplayName("Should normalize when MIME is unknown but extension is .m4a")
    void validateMimeType_WhenOctetStreamWithM4aExtension_ShouldReturnAudioMp4() {
        MockMultipartFile octetM4a =
                new MockMultipartFile(
                        "file",
                        "recording.m4a",
                        "application/octet-stream",
                        "fake-bytes".getBytes());
        assertEquals("audio/mp4", voiceService.validateAndNormalizeMimeType(octetM4a));
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when file is empty or null")
    void validateMimeType_WhenFileEmptyOrNull_ShouldThrowException() {
        MockMultipartFile emptyFile =
                new MockMultipartFile("file", "empty.m4a", "audio/mp4", new byte[0]);

        assertThrows(
                IllegalArgumentException.class,
                () -> voiceService.validateAndNormalizeMimeType(emptyFile));
        assertThrows(
                IllegalArgumentException.class,
                () -> voiceService.validateAndNormalizeMimeType(null));
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when audio format is unsupported")
    void validateMimeType_WhenUnsupportedType_ShouldThrowException() {
        MockMultipartFile pdfFile =
                new MockMultipartFile(
                        "file", "file.pdf", "application/pdf", "fake-bytes".getBytes());
        assertThrows(
                IllegalArgumentException.class,
                () -> voiceService.validateAndNormalizeMimeType(pdfFile));
    }

    @Test
    @DisplayName("Should parse valid Gemini response correctly")
    void parseGeminiResponse_WhenValidResponse_ShouldReturnVoiceResponse() throws IOException {
        String rawGeminiJson =
                """
        {
            "candidates": [
                {
                    "content": {
                        "parts": [
                            {
                                "text": "{\\"amount\\": 45000, \\"type\\": \\"EXPENSE\\", \\"category\\": \\"Dining\\", \\"note\\": \\"Ăn bún bò\\", \\"transcript\\": \\"Hôm nay ăn bún bò 45 nghìn\\"}"
                            }
                        ]
                    }
                }
            ]
        }
        """;

        List<String> categories = List.of("Dining", "Transport", "Shopping");
        VoiceResponse response = voiceService.parseGeminiResponse(rawGeminiJson, categories);

        assertNotNull(response);
        assertEquals(new BigDecimal("45000"), response.amount());
        assertEquals("EXPENSE", response.type());
        assertEquals("Dining", response.category());
        assertEquals("Ăn bún bò", response.note());
        assertEquals("Hôm nay ăn bún bò 45 nghìn", response.transcript());
    }

    @Test
    @DisplayName("Should fallback category to 'Unknown' when not in valid categories")
    void parseGeminiResponse_WhenCategoryNotInList_ShouldFallbackToUnknown() throws IOException {
        String rawGeminiJson =
                """
        {
            "candidates": [
                {
                    "content": {
                        "parts": [
                            {
                                "text": "{\\"amount\\": 100000, \\"type\\": \\"EXPENSE\\", \\"category\\": \\"Cosmetics\\", \\"note\\": \\"Mua mỹ phẩm\\", \\"transcript\\": \\"Mua mỹ phẩm 100 nghìn\\"}"
                            }
                        ]
                    }
                }
            ]
        }
        """;

        List<String> categories = List.of("Dining", "Transport");
        VoiceResponse response = voiceService.parseGeminiResponse(rawGeminiJson, categories);

        assertEquals("Unknown", response.category());
        assertEquals("EXPENSE", response.type());
        assertEquals(new BigDecimal("100000"), response.amount());
    }
}

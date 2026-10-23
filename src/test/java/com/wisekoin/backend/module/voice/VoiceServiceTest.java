package com.wisekoin.backend.module.voice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.wisekoin.backend.module.category.Category;
import com.wisekoin.backend.module.category.CategoryService;
import com.wisekoin.backend.module.user.UserService;
import com.wisekoin.backend.module.voice.dto.VoiceResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

class VoiceServiceTest {
    private VoiceService voiceService;
    private JsonMapper jsonMapper;

    @BeforeEach
    void setUp() {
        jsonMapper = new JsonMapper();
        voiceService =
                new VoiceService(
                        mock(RestClient.class),
                        jsonMapper,
                        mock(CategoryService.class),
                        mock(UserService.class));
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
    @DisplayName("Should normalize an octet-stream file with an m4a extension")
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
    @DisplayName("Should reject empty, null, and unsupported audio files")
    void validateMimeType_WhenInvalid_ShouldThrowException() {
        MockMultipartFile emptyFile =
                new MockMultipartFile("file", "empty.m4a", "audio/mp4", new byte[0]);
        MockMultipartFile pdfFile =
                new MockMultipartFile(
                        "file", "file.pdf", "application/pdf", "fake-bytes".getBytes());

        assertThrows(
                IllegalArgumentException.class,
                () -> voiceService.validateAndNormalizeMimeType(emptyFile));
        assertThrows(
                IllegalArgumentException.class,
                () -> voiceService.validateAndNormalizeMimeType(null));
        assertThrows(
                IllegalArgumentException.class,
                () -> voiceService.validateAndNormalizeMimeType(pdfFile));
    }

    @Test
    @DisplayName("Should build a currency-aware prompt with typed category IDs")
    void buildRequest_ShouldUseCurrencyAndTypedCategoryIds() throws IOException {
        List<Category> categories =
                List.of(
                        category(10L, "Dining", Category.CategoryType.EXPENSE),
                        category(20L, "Salary", Category.CategoryType.INCOME));

        Map<String, Object> request =
                voiceService.buildGeminiRequestBody("audio/mp4", "audio-data", categories, "USD");
        String json = jsonMapper.writeValueAsString(request);

        assertTrue(json.contains("USD"));
        assertTrue(json.contains("10 = Dining"));
        assertTrue(json.contains("20 = Salary"));
        assertTrue(json.contains("category_id"));
    }

    @Test
    @DisplayName("Should parse a valid response using the stable category ID")
    void parseGeminiResponse_WhenValidResponse_ShouldReturnVoiceResponse() throws IOException {
        Category dining = category(10L, "Dining", Category.CategoryType.EXPENSE);
        String rawGeminiJson =
                geminiResponse(
                        "45000.25", "EXPENSE", "10", "Ăn bún bò", "Hôm nay ăn bún bò 45 nghìn");

        VoiceResponse response =
                voiceService.parseGeminiResponse(rawGeminiJson, List.of(dining), "VND");

        assertEquals(new BigDecimal("45000.25"), response.amount());
        assertEquals("VND", response.currency());
        assertEquals("EXPENSE", response.type());
        assertEquals(10L, response.categoryId());
        assertEquals("Dining", response.category());
        assertEquals("Ăn bún bò", response.note());
        assertEquals("Hôm nay ăn bún bò 45 nghìn", response.transcript());
    }

    @Test
    @DisplayName("Should reject an income category for an expense result")
    void parseGeminiResponse_WhenCategoryTypeDoesNotMatch_ShouldReturnUnknownCategory()
            throws IOException {
        Category salary = category(20L, "Salary", Category.CategoryType.INCOME);
        String rawGeminiJson =
                geminiResponse("100000", "EXPENSE", "20", "Purchase", "Spent 100000");

        VoiceResponse response =
                voiceService.parseGeminiResponse(rawGeminiJson, List.of(salary), "VND");

        assertEquals("EXPENSE", response.type());
        assertNull(response.categoryId());
        assertEquals("Unknown", response.category());
    }

    @Test
    @DisplayName("Should preserve ambiguity instead of defaulting an invalid type to expense")
    void parseGeminiResponse_WhenTypeIsInvalid_ShouldReturnUnknownTypeAndCategory()
            throws IOException {
        Category dining = category(10L, "Dining", Category.CategoryType.EXPENSE);
        String rawGeminiJson =
                geminiResponse("100000", "OTHER", "10", "Transfer", "Received or sent 100000");

        VoiceResponse response =
                voiceService.parseGeminiResponse(rawGeminiJson, List.of(dining), "USD");

        assertEquals(VoiceService.UNKNOWN_TYPE, response.type());
        assertEquals("USD", response.currency());
        assertNull(response.categoryId());
        assertEquals("Unknown", response.category());
    }

    @Test
    @DisplayName("Should reject non-positive amounts")
    void parseGeminiResponse_WhenAmountIsNotPositive_ShouldThrow() {
        String rawGeminiJson =
                geminiResponse("0", "EXPENSE", VoiceService.UNKNOWN_CATEGORY_ID, "", "");

        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        () -> voiceService.parseGeminiResponse(rawGeminiJson, List.of(), "VND"));

        assertTrue(exception.getMessage().contains("positive amount"));
    }

    private Category category(Long id, String name, Category.CategoryType type) {
        Category category = new Category();
        category.setId(id);
        category.setName(name);
        category.setType(type);
        return category;
    }

    private String geminiResponse(
            String amount, String type, String categoryId, String note, String transcript) {
        String extracted =
                "{\"amount\":"
                        + amount
                        + ",\"type\":\""
                        + type
                        + "\",\"category_id\":\""
                        + categoryId
                        + "\",\"note\":\""
                        + note
                        + "\",\"transcript\":\""
                        + transcript
                        + "\"}";
        return "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":"
                + quoteJson(extracted)
                + "}]}}]}";
    }

    private String quoteJson(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}

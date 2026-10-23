package com.wisekoin.backend.module.voice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.wisekoin.backend.module.ai.AiDuplicateDetector;
import com.wisekoin.backend.module.ai.adaptive.AiAdaptationService;
import com.wisekoin.backend.module.ai.adaptive.AiConfidenceScorer;
import com.wisekoin.backend.module.ai.context.AiRequestContextResolver;
import com.wisekoin.backend.module.ai.dto.AiDuplicateCandidate;
import com.wisekoin.backend.module.ai.prompt.AiPromptService;
import com.wisekoin.backend.module.ai.provider.AiExtractionClient;
import com.wisekoin.backend.module.category.Category;
import com.wisekoin.backend.module.category.CategoryService;
import com.wisekoin.backend.module.user.UserService;
import com.wisekoin.backend.module.voice.dto.VoiceResponse;
import com.wisekoin.backend.module.wallet.Wallet;
import com.wisekoin.backend.module.wallet.WalletRepository;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import tools.jackson.databind.json.JsonMapper;

class VoiceServiceTest {
    private VoiceService voiceService;
    private JsonMapper jsonMapper;
    private AiDuplicateDetector duplicateDetector;

    @BeforeEach
    void setUp() {
        jsonMapper = new JsonMapper();
        duplicateDetector = mock(AiDuplicateDetector.class);
        when(duplicateDetector.findCandidates(
                        anyLong(),
                        any(BigDecimal.class),
                        anyString(),
                        nullable(LocalDateTime.class),
                        anyString()))
                .thenReturn(List.of());
        voiceService =
                new VoiceService(
                        mock(AiExtractionClient.class),
                        mock(AiPromptService.class),
                        mock(AiRequestContextResolver.class),
                        mock(AiAdaptationService.class),
                        mock(AiConfidenceScorer.class),
                        jsonMapper,
                        mock(CategoryService.class),
                        mock(UserService.class),
                        duplicateDetector,
                        mock(WalletRepository.class),
                        Clock.fixed(Instant.parse("2026-10-23T10:00:00Z"), ZoneOffset.UTC));
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
    @DisplayName("Should build a typed response schema with owned category and wallet IDs")
    void buildSchema_ShouldUseTypedCategoryAndWalletIds() throws IOException {
        List<Category> categories =
                List.of(
                        category(10L, "Dining", Category.CategoryType.EXPENSE),
                        category(20L, "Salary", Category.CategoryType.INCOME));

        Wallet wallet = new Wallet();
        wallet.setId(30L);
        wallet.setName("Cash");
        Map<String, Object> schema =
                voiceService.buildResponseSchema(categories, List.of(wallet), "USD");
        String json = jsonMapper.writeValueAsString(schema);

        assertTrue(json.contains("USD"));
        assertTrue(json.contains("\"10\""));
        assertTrue(json.contains("\"20\""));
        assertTrue(json.contains("\"30\""));
        assertTrue(json.contains("category_id"));
        assertTrue(json.contains("source_wallet_id"));
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

    @Test
    @DisplayName(
            "Should extract multiple transactions, relative dates, transfers, and ranked candidates")
    void parseGeminiResponse_WhenBatchIsValid_ShouldReturnAllTransactions() throws IOException {
        Category dining = category(10L, "Dining", Category.CategoryType.EXPENSE);
        Category transport = category(11L, "Transport", Category.CategoryType.EXPENSE);
        String extracted =
                """
                {
                  "transcript":"Yesterday lunch was 50000 and I moved 200000 to savings",
                  "transactions":[
                    {
                      "amount":50000,
                      "type":"EXPENSE",
                      "category_id":"10",
                      "category_candidates":["10","11"],
                      "note":"Lunch",
                      "occurred_at":"2026-10-22T12:00:00",
                      "source_wallet_hint":"Cash",
                      "destination_wallet_hint":"",
                      "ambiguities":[]
                    },
                    {
                      "amount":200000,
                      "type":"TRANSFER",
                      "category_id":"UNKNOWN",
                      "category_candidates":[],
                      "note":"Move to savings",
                      "occurred_at":"2026-10-23T09:00:00",
                      "source_wallet_hint":"Cash",
                      "destination_wallet_hint":"Savings",
                      "ambiguities":[]
                    }
                  ]
                }
                """;

        VoiceResponse response =
                voiceService.parseGeminiResponse(
                        geminiResponse(extracted), List.of(dining, transport), "VND", 1L);

        assertEquals(2, response.transactions().size());
        assertEquals(LocalDateTime.of(2026, 10, 22, 12, 0), response.occurredAt());
        assertEquals(
                List.of(10L, 11L),
                response.transactions().getFirst().categoryCandidates().stream()
                        .map(candidate -> candidate.categoryId())
                        .toList());
        assertEquals("TRANSFER", response.transactions().get(1).type());
        assertEquals("Savings", response.transactions().get(1).destinationWalletHint());
        assertTrue(response.transactions().get(1).requiresClarification());
    }

    @Test
    @DisplayName("Should surface duplicate candidates before a voice transaction is saved")
    void parseGeminiResponse_WhenDuplicateExists_ShouldRequireClarification() throws IOException {
        AiDuplicateCandidate duplicate =
                new AiDuplicateCandidate(
                        88L,
                        new BigDecimal("100000"),
                        "EXPENSE",
                        LocalDateTime.of(2026, 10, 23, 9, 0),
                        List.of("EXACT_AMOUNT"));
        when(duplicateDetector.findCandidates(
                        anyLong(),
                        any(BigDecimal.class),
                        anyString(),
                        nullable(LocalDateTime.class),
                        anyString()))
                .thenReturn(List.of(duplicate));
        String rawGeminiJson =
                geminiResponse("100000", "EXPENSE", "UNKNOWN", "Purchase", "Spent 100000");

        VoiceResponse response =
                voiceService.parseGeminiResponse(rawGeminiJson, List.of(), "VND", 1L);

        assertTrue(response.requiresClarification());
        assertEquals(1, response.transactions().getFirst().duplicateCandidates().size());
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
        return geminiResponse(extracted);
    }

    private String geminiResponse(String extracted) {
        return "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":"
                + quoteJson(extracted)
                + "}]}}]}";
    }

    private String quoteJson(String value) {
        return "\""
                + value.replace("\\", "\\\\")
                        .replace("\"", "\\\"")
                        .replace("\r", "\\r")
                        .replace("\n", "\\n")
                + "\"";
    }
}

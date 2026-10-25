package com.wisekoin.backend.evaluation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wisekoin.backend.module.ai.media.AiMediaIngestionService;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class VietnameseAiCorpusTest {
    private static final Pattern EMAIL =
            Pattern.compile("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", Pattern.CASE_INSENSITIVE);
    private static final Pattern PHONE = Pattern.compile("(?<![\\d*])(?:\\+?84|0)\\d{8,10}(?!\\d)");
    private static final Pattern LONG_UNMASKED_NUMBER =
            Pattern.compile("(?<![\\d*])\\d{8,16}(?!\\d)");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AiMediaIngestionService mediaIngestionService =
            new AiMediaIngestionService(
                    5_242_880, 5_242_880, 8192, 8192, 20_000_000, java.time.Duration.ofSeconds(60));

    @Test
    void corpusIsDeidentifiedCompleteAndMetricSafe() throws IOException {
        Path corpusRoot =
                Path.of("evaluation", "vietnamese-ai-corpus").toAbsolutePath().normalize();
        Path manifestPath = corpusRoot.resolve("manifest.json");
        assertTrue(Files.isRegularFile(manifestPath));
        assertTrue(Files.isRegularFile(corpusRoot.resolve("manifest.schema.json")));

        JsonNode manifest = objectMapper.readTree(manifestPath.toFile());
        assertEquals("1.1.0", manifest.path("corpus_version").asText());
        JsonNode cases = manifest.path("cases");
        assertTrue(cases.isArray());
        assertTrue(cases.size() >= 20, "The seed corpus must retain representative coverage");

        Set<String> ids = new HashSet<>();
        Set<String> coverageTags = new HashSet<>();
        int receiptCount = 0;
        int speechCount = 0;
        int bankCount = 0;
        int publicFormatCount = 0;

        for (JsonNode corpusCase : cases) {
            String id = requiredText(corpusCase, "id");
            assertTrue(ids.add(id), "Duplicate corpus case ID: " + id);
            assertEquals("vi-VN", requiredText(corpusCase, "locale"));

            JsonNode provenance = corpusCase.path("provenance");
            assertTrue(provenance.path("deidentified").asBoolean(), id + " is not de-identified");
            String provenanceKind = requiredText(provenance, "kind");
            if ("SYNTHETIC".equals(provenanceKind)) {
                assertTrue(
                        provenance.path("consent_record_id").isNull(),
                        "Synthetic cases must not claim a human consent record");
            } else if ("CONSENTED".equals(provenanceKind)) {
                assertEquals("CONSENTED", provenanceKind);
                assertFalse(requiredText(provenance, "consent_record_id").isBlank());
            } else {
                assertEquals("PUBLIC_FORMAT_DERIVED", provenanceKind);
                assertTrue(provenance.path("consent_record_id").isNull());
                assertTrue(requiredText(provenance, "source_url").startsWith("https://"));
                assertTrue(
                        requiredText(provenance, "source_accessed_at")
                                .matches("\\d{4}-\\d{2}-\\d{2}"));
                assertFalse(requiredText(provenance, "derivation").isBlank());
                publicFormatCount++;
            }

            JsonNode input = corpusCase.path("input");
            Path asset = corpusRoot.resolve(requiredText(input, "asset")).normalize();
            assertTrue(asset.startsWith(corpusRoot), id + " asset escaped the corpus directory");
            assertTrue(Files.isRegularFile(asset), id + " asset is missing: " + asset);
            scanTextAssetForDirectIdentifiers(asset, input.path("format").asText(), id);
            validateMediaWithProductionIngestion(asset, input.path("format").asText(), id);

            JsonNode expected = corpusCase.path("expected");
            assertNotNull(expected.get("requires_clarification"), id);
            if (!expected.path("amount").isNull()) {
                BigDecimal amount =
                        assertDoesNotThrow(
                                () -> new BigDecimal(requiredText(expected, "amount")), id);
                assertTrue(amount.signum() > 0, id + " must use a positive amount");
                assertTrue(amount.scale() <= 4, id + " amount exceeds application scale");
                assertTrue(
                        requiredText(expected, "currency").matches("[A-Z]{3}"),
                        id + " currency is invalid");
            }

            corpusCase.path("tags").forEach(tag -> coverageTags.add(tag.asText()));
            String modality = requiredText(corpusCase, "modality");
            switch (modality) {
                case "RECEIPT_IMAGE" -> receiptCount++;
                case "SPEECH" -> {
                    speechCount++;
                    assertEquals("SYNTHETIC_MEDIA", requiredText(input, "media_status"));
                    Path transcript =
                            corpusRoot.resolve(requiredText(input, "transcript_asset")).normalize();
                    assertTrue(transcript.startsWith(corpusRoot));
                    assertTrue(Files.isRegularFile(transcript), id + " transcript is missing");
                    scanTextAssetForDirectIdentifiers(transcript, "text/plain", id);
                    corpusCase
                            .path("eligible_metrics")
                            .forEach(
                                    metric ->
                                            assertFalse(
                                                    Set.of(
                                                                    "speech_to_text",
                                                                    "acoustic_noise",
                                                                    "end_to_end_audio",
                                                                    "speech_to_text_accuracy")
                                                            .contains(metric.asText()),
                                                    id
                                                            + " transcript proxy is incorrectly eligible for acoustic metrics"));
                }
                case "BANK_NOTIFICATION" -> {
                    bankCount++;
                    if ("PUBLIC_FORMAT_DERIVED".equals(provenanceKind)) {
                        assertEquals(
                                "DEIDENTIFIED_TEXT_TEMPLATE", requiredText(input, "media_status"));
                        assertFalse(requiredText(input, "source_id").isBlank());
                        assertTrue(
                                Set.of("APP_PUSH", "SMS", "IN_APP")
                                        .contains(requiredText(input, "channel")));
                        assertFalse(requiredText(input, "template_version").isBlank());
                    }
                }
                default -> throw new AssertionError("Unsupported modality in " + id);
            }
        }

        assertTrue(receiptCount >= 6);
        assertTrue(speechCount >= 8);
        assertTrue(bankCount >= 13);
        assertTrue(publicFormatCount >= 7);
        assertTrue(
                coverageTags.containsAll(
                        Set.of(
                                "receipt",
                                "speech",
                                "bank_notification",
                                "regional_north",
                                "regional_central",
                                "regional_south",
                                "currency_expression",
                                "noisy",
                                "adversarial",
                                "false_balance_change")));

        try (var sources = Files.walk(corpusRoot.resolve("source"))) {
            for (Path source : sources.filter(Files::isRegularFile).toList()) {
                scanTextAssetForDirectIdentifiers(source, "text/plain", source.toString());
            }
        }
    }

    private String requiredText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        assertNotNull(value, "Missing field: " + field);
        assertTrue(value.isTextual(), "Field must be text: " + field);
        assertFalse(value.asText().isBlank(), "Field must not be blank: " + field);
        return value.asText();
    }

    private void scanTextAssetForDirectIdentifiers(Path asset, String format, String id)
            throws IOException {
        if (!"text/plain".equals(format)) {
            return;
        }
        String content = Files.readString(asset, StandardCharsets.UTF_8);
        assertFalse(EMAIL.matcher(content).find(), id + " contains an email address");
        assertFalse(PHONE.matcher(content).find(), id + " contains a phone number");
        assertFalse(
                LONG_UNMASKED_NUMBER.matcher(content).find(),
                id + " contains a possible unmasked account/card number");
    }

    private void validateMediaWithProductionIngestion(Path asset, String format, String id)
            throws IOException {
        if (!Set.of("image/png", "audio/mp4").contains(format)) {
            return;
        }
        MockMultipartFile upload =
                new MockMultipartFile(
                        "file", asset.getFileName().toString(), format, Files.readAllBytes(asset));
        assertDoesNotThrow(
                () -> {
                    if ("image/png".equals(format)) {
                        mediaIngestionService.ingestReceipt(upload);
                    } else {
                        mediaIngestionService.ingestVoice(upload);
                    }
                },
                id + " is not accepted by the production media-ingestion boundary");
    }
}

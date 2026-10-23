package com.wisekoin.backend.module.ai.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wisekoin.backend.module.common.exception.BadRequestException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

class AiMediaIngestionServiceTest {
    @Test
    void streamsValidPngAndComputesStableFingerprintWithoutCallingGetBytes() throws Exception {
        byte[] png = pngHeader(1200, 800);
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn((long) png.length);
        when(file.getContentType()).thenReturn("image/png");
        when(file.getInputStream()).thenAnswer(ignored -> new ByteArrayInputStream(png));

        AiMediaPayload payload = service().ingestReceipt(file);

        assertEquals("image/png", payload.mimeType());
        assertEquals(Base64.getEncoder().encodeToString(png), payload.base64Content());
        assertEquals(64, payload.fingerprint().length());
        verify(file, never()).getBytes();
    }

    @Test
    void rejectsDeclaredTypeMismatchAndExcessiveImageDimensions() {
        MockMultipartFile mismatch =
                new MockMultipartFile("file", "fake.jpg", "image/jpeg", pngHeader(100, 100));
        MockMultipartFile oversized =
                new MockMultipartFile("file", "huge.png", "image/png", pngHeader(5000, 5000));

        assertThrows(BadRequestException.class, () -> service().ingestReceipt(mismatch));
        assertThrows(BadRequestException.class, () -> service().ingestReceipt(oversized));
    }

    @Test
    void acceptsBoundedM4aAndRejectsLongOrDisguisedAudio() throws Exception {
        AiMediaPayload valid =
                service()
                        .ingestVoice(
                                new MockMultipartFile(
                                        "file",
                                        "voice.m4a",
                                        "audio/m4a",
                                        mp4WithDuration(60_000, 1000)));

        assertEquals("audio/mp4", valid.mimeType());
        assertThrows(
                BadRequestException.class,
                () ->
                        service()
                                .ingestVoice(
                                        new MockMultipartFile(
                                                "file",
                                                "long.m4a",
                                                "audio/mp4",
                                                mp4WithDuration(60_001, 1000))));
        assertThrows(
                BadRequestException.class,
                () ->
                        service()
                                .ingestVoice(
                                        new MockMultipartFile(
                                                "file",
                                                "fake.m4a",
                                                "audio/mp4",
                                                "not-an-mp4".getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    void rejectsExcessivelyNestedMp4Boxes() throws Exception {
        byte[] nested = box("mvhd", movieHeaderPayload(1_000, 1_000));
        for (int depth = 0; depth < 18; depth++) {
            nested = box("moov", nested);
        }
        ByteArrayOutputStream file = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(file)) {
            output.writeInt(16);
            output.writeBytes("ftyp");
            output.writeBytes("isom");
            output.writeInt(0);
            output.write(nested);
        }

        MockMultipartFile upload =
                new MockMultipartFile("file", "nested.m4a", "audio/mp4", file.toByteArray());

        assertThrows(BadRequestException.class, () -> service().ingestVoice(upload));
    }

    private AiMediaIngestionService service() {
        return new AiMediaIngestionService(
                5_242_880, 5_242_880, 8192, 8192, 20_000_000, Duration.ofSeconds(60));
    }

    private byte[] pngHeader(int width, int height) {
        byte[] value = new byte[30];
        byte[] signature = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        System.arraycopy(signature, 0, value, 0, signature.length);
        value[11] = 13;
        System.arraycopy("IHDR".getBytes(StandardCharsets.US_ASCII), 0, value, 12, 4);
        writeInt(value, 16, width);
        writeInt(value, 20, height);
        return value;
    }

    private byte[] mp4WithDuration(int duration, int timescale) throws IOException {
        byte[] movie = box("moov", box("mvhd", movieHeaderPayload(duration, timescale)));

        ByteArrayOutputStream file = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(file)) {
            output.writeInt(16);
            output.writeBytes("ftyp");
            output.writeBytes("isom");
            output.writeInt(0);
            output.write(movie);
        }
        return file.toByteArray();
    }

    private byte[] movieHeaderPayload(int duration, int timescale) throws IOException {
        ByteArrayOutputStream movieHeaderPayload = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(movieHeaderPayload)) {
            output.writeInt(0);
            output.writeInt(0);
            output.writeInt(0);
            output.writeInt(timescale);
            output.writeInt(duration);
        }
        return movieHeaderPayload.toByteArray();
    }

    private byte[] box(String type, byte[] payload) throws IOException {
        ByteArrayOutputStream box = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(box)) {
            output.writeInt(payload.length + 8);
            output.writeBytes(type);
            output.write(payload);
        }
        return box.toByteArray();
    }

    private void writeInt(byte[] target, int offset, int value) {
        target[offset] = (byte) (value >>> 24);
        target[offset + 1] = (byte) (value >>> 16);
        target[offset + 2] = (byte) (value >>> 8);
        target[offset + 3] = (byte) value;
    }
}

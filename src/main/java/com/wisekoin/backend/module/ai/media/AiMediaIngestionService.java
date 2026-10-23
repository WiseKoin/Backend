package com.wisekoin.backend.module.ai.media;

import com.wisekoin.backend.module.common.exception.BadRequestException;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AiMediaIngestionService {
    private static final Set<Integer> JPEG_START_OF_FRAME_MARKERS =
            Set.of(0xC0, 0xC1, 0xC2, 0xC3, 0xC5, 0xC6, 0xC7, 0xC9, 0xCA, 0xCB, 0xCD, 0xCE, 0xCF);
    private static final int STREAM_BUFFER_SIZE = 8192;
    private static final int MAX_MP4_BOX_DEPTH = 16;

    private final long maxImageBytes;
    private final long maxAudioBytes;
    private final int maxImageWidth;
    private final int maxImageHeight;
    private final long maxImagePixels;
    private final Duration maxAudioDuration;

    public AiMediaIngestionService(
            @Value("${app.ai.media.image.max-bytes:5242880}") long maxImageBytes,
            @Value("${app.ai.media.audio.max-bytes:5242880}") long maxAudioBytes,
            @Value("${app.ai.media.image.max-width:8192}") int maxImageWidth,
            @Value("${app.ai.media.image.max-height:8192}") int maxImageHeight,
            @Value("${app.ai.media.image.max-pixels:20000000}") long maxImagePixels,
            @Value("${app.ai.media.audio.max-duration:60s}") Duration maxAudioDuration) {
        if (maxImageBytes < 1
                || maxAudioBytes < 1
                || maxImageWidth < 1
                || maxImageHeight < 1
                || maxImagePixels < 1
                || maxAudioDuration.isNegative()
                || maxAudioDuration.isZero()) {
            throw new IllegalArgumentException("AI media limits must be positive.");
        }
        this.maxImageBytes = maxImageBytes;
        this.maxAudioBytes = maxAudioBytes;
        this.maxImageWidth = maxImageWidth;
        this.maxImageHeight = maxImageHeight;
        this.maxImagePixels = maxImagePixels;
        this.maxAudioDuration = maxAudioDuration;
    }

    public AiMediaPayload ingestReceipt(MultipartFile file) throws IOException {
        requireSize(file, maxImageBytes, "receipt image");
        DetectedImage image = detectImage(file);
        validateDeclaredType(file.getContentType(), image.mimeType());
        validateDimensions(image.width(), image.height());
        return encodeAndFingerprint(file, image.mimeType(), maxImageBytes);
    }

    public AiMediaPayload ingestVoice(MultipartFile file) throws IOException {
        requireSize(file, maxAudioBytes, "voice recording");
        validateDeclaredType(file.getContentType(), "audio/mp4");
        Duration duration = readMp4Duration(file);
        if (duration.isZero() || duration.isNegative()) {
            throw new BadRequestException("Voice recording duration is invalid.");
        }
        if (duration.compareTo(maxAudioDuration) > 0) {
            throw new BadRequestException(
                    "Voice recording exceeds the "
                            + maxAudioDuration.toSeconds()
                            + " second limit.");
        }
        return encodeAndFingerprint(file, "audio/mp4", maxAudioBytes);
    }

    private void requireSize(MultipartFile file, long maxBytes, String label) {
        if (file == null || file.isEmpty() || file.getSize() <= 0) {
            throw new BadRequestException("Uploaded " + label + " cannot be empty.");
        }
        if (file.getSize() > maxBytes) {
            throw new BadRequestException("Uploaded " + label + " exceeds the size limit.");
        }
    }

    private DetectedImage detectImage(MultipartFile file) throws IOException {
        byte[] prefix = new byte[30];
        int read;
        try (InputStream input = file.getInputStream()) {
            read = input.readNBytes(prefix, 0, prefix.length);
        }
        if (read >= 24 && isPng(prefix)) {
            int width = readIntBigEndian(prefix, 16);
            int height = readIntBigEndian(prefix, 20);
            return new DetectedImage("image/png", width, height);
        }
        if (read >= 30 && isWebp(prefix)) {
            return detectWebp(prefix);
        }
        if (read >= 2 && unsigned(prefix[0]) == 0xFF && unsigned(prefix[1]) == 0xD8) {
            return detectJpeg(file);
        }
        throw new BadRequestException(
                "Receipt content is not a supported JPEG, PNG, or WebP image.");
    }

    private DetectedImage detectJpeg(MultipartFile file) throws IOException {
        try (DataInputStream input =
                new DataInputStream(new BufferedInputStream(file.getInputStream()))) {
            if (input.readUnsignedShort() != 0xFFD8) {
                throw new BadRequestException("Receipt content is not a valid JPEG image.");
            }
            while (true) {
                int value;
                do {
                    value = input.readUnsignedByte();
                } while (value != 0xFF);
                int marker;
                do {
                    marker = input.readUnsignedByte();
                } while (marker == 0xFF);

                if (marker == 0xD9 || marker == 0xDA) {
                    break;
                }
                if (marker == 0x01 || marker >= 0xD0 && marker <= 0xD7) {
                    continue;
                }
                int segmentLength = input.readUnsignedShort();
                if (segmentLength < 2) {
                    break;
                }
                if (JPEG_START_OF_FRAME_MARKERS.contains(marker)) {
                    input.readUnsignedByte();
                    int height = input.readUnsignedShort();
                    int width = input.readUnsignedShort();
                    return new DetectedImage("image/jpeg", width, height);
                }
                input.skipNBytes(segmentLength - 2L);
            }
        } catch (EOFException exception) {
            throw new BadRequestException("Receipt content is a truncated JPEG image.");
        }
        throw new BadRequestException("Receipt content does not contain valid JPEG dimensions.");
    }

    private DetectedImage detectWebp(byte[] prefix) {
        String chunkType = new String(prefix, 12, 4, StandardCharsets.US_ASCII);
        if ("VP8X".equals(chunkType)) {
            int width = 1 + read24LittleEndian(prefix, 24);
            int height = 1 + read24LittleEndian(prefix, 27);
            return new DetectedImage("image/webp", width, height);
        }
        if ("VP8L".equals(chunkType) && unsigned(prefix[20]) == 0x2F) {
            int width = 1 + unsigned(prefix[21]) + ((unsigned(prefix[22]) & 0x3F) << 8);
            int height =
                    1
                            + ((unsigned(prefix[22]) & 0xC0) >> 6)
                            + (unsigned(prefix[23]) << 2)
                            + ((unsigned(prefix[24]) & 0x0F) << 10);
            return new DetectedImage("image/webp", width, height);
        }
        if ("VP8 ".equals(chunkType)
                && unsigned(prefix[23]) == 0x9D
                && unsigned(prefix[24]) == 0x01
                && unsigned(prefix[25]) == 0x2A) {
            int width = (unsigned(prefix[26]) | unsigned(prefix[27]) << 8) & 0x3FFF;
            int height = (unsigned(prefix[28]) | unsigned(prefix[29]) << 8) & 0x3FFF;
            return new DetectedImage("image/webp", width, height);
        }
        throw new BadRequestException("Receipt content has an unsupported WebP header.");
    }

    private Duration readMp4Duration(MultipartFile file) throws IOException {
        try (DataInputStream input =
                new DataInputStream(new BufferedInputStream(file.getInputStream()))) {
            long firstSize = Integer.toUnsignedLong(input.readInt());
            String firstType = readType(input);
            if (!"ftyp".equals(firstType) || firstSize < 8 || firstSize > file.getSize()) {
                throw new BadRequestException("Voice content is not a valid MP4/M4A recording.");
            }
        } catch (EOFException exception) {
            throw new BadRequestException("Voice content is a truncated MP4/M4A recording.");
        }

        try (DataInputStream input =
                new DataInputStream(new BufferedInputStream(file.getInputStream()))) {
            Duration duration = findMovieDuration(input, file.getSize(), 0);
            if (duration == null) {
                throw new BadRequestException(
                        "Voice recording does not contain duration metadata.");
            }
            return duration;
        }
    }

    private Duration findMovieDuration(DataInputStream input, long remaining, int depth)
            throws IOException {
        if (depth > MAX_MP4_BOX_DEPTH) {
            throw new BadRequestException("Voice content contains excessive MP4 box nesting.");
        }
        long consumed = 0;
        while (remaining - consumed >= 8) {
            long size = Integer.toUnsignedLong(input.readInt());
            String type = readType(input);
            long headerSize = 8;
            if (size == 1) {
                size = input.readLong();
                headerSize = 16;
            } else if (size == 0) {
                size = remaining - consumed;
            }
            if (size < headerSize || size > remaining - consumed) {
                throw new BadRequestException("Voice content contains an invalid MP4 box.");
            }
            long payloadSize = size - headerSize;
            if ("mvhd".equals(type)) {
                return readMovieHeaderDuration(input, payloadSize);
            }
            if ("moov".equals(type)) {
                Duration duration = findMovieDuration(input, payloadSize, depth + 1);
                if (duration != null) {
                    return duration;
                }
            } else {
                input.skipNBytes(payloadSize);
            }
            consumed += size;
        }
        if (consumed < remaining) {
            input.skipNBytes(remaining - consumed);
        }
        return null;
    }

    private Duration readMovieHeaderDuration(DataInputStream input, long payloadSize)
            throws IOException {
        if (payloadSize < 20) {
            throw new BadRequestException("Voice content has invalid duration metadata.");
        }
        int version = input.readUnsignedByte();
        input.skipNBytes(3);
        long timescale;
        long duration;
        if (version == 0) {
            input.skipNBytes(8);
            timescale = Integer.toUnsignedLong(input.readInt());
            duration = Integer.toUnsignedLong(input.readInt());
        } else if (version == 1 && payloadSize >= 32) {
            input.skipNBytes(16);
            timescale = Integer.toUnsignedLong(input.readInt());
            duration = input.readLong();
        } else {
            throw new BadRequestException("Voice content uses unsupported duration metadata.");
        }
        if (timescale <= 0 || duration <= 0) {
            throw new BadRequestException("Voice recording duration is invalid.");
        }
        long seconds = duration / timescale;
        long remainder = duration % timescale;
        long nanos = Math.round(remainder * 1_000_000_000.0 / timescale);
        return Duration.ofSeconds(seconds, Math.min(nanos, 999_999_999));
    }

    private AiMediaPayload encodeAndFingerprint(MultipartFile file, String mimeType, long maxBytes)
            throws IOException {
        MessageDigest digest = sha256();
        int encodedCapacity = Math.toIntExact((file.getSize() + 2) / 3 * 4);
        StringBuilder encoded = new StringBuilder(encodedCapacity);
        long total = 0;
        try (DigestInputStream input =
                        new DigestInputStream(
                                new BufferedInputStream(file.getInputStream()), digest);
                OutputStream output =
                        Base64.getEncoder().wrap(new StringBuilderOutputStream(encoded))) {
            byte[] buffer = new byte[STREAM_BUFFER_SIZE];
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) {
                    throw new BadRequestException("Uploaded media exceeds the size limit.");
                }
                output.write(buffer, 0, read);
            }
        }
        return new AiMediaPayload(
                mimeType, encoded.toString(), HexFormat.of().formatHex(digest.digest()), total);
    }

    private void validateDeclaredType(String declaredType, String detectedType) {
        if (declaredType == null || declaredType.isBlank()) {
            return;
        }
        String normalized = declaredType.trim().toLowerCase();
        boolean matches =
                detectedType.equals(normalized)
                        || "image/jpeg".equals(detectedType) && "image/jpg".equals(normalized)
                        || "audio/mp4".equals(detectedType)
                                && Set.of("audio/m4a", "audio/x-m4a").contains(normalized);
        if (!matches) {
            throw new BadRequestException("Uploaded media type does not match its content.");
        }
    }

    private void validateDimensions(int width, int height) {
        if (width < 1 || height < 1) {
            throw new BadRequestException("Receipt image dimensions are invalid.");
        }
        long pixels = (long) width * height;
        if (width > maxImageWidth || height > maxImageHeight || pixels > maxImagePixels) {
            throw new BadRequestException("Receipt image dimensions exceed the safe limit.");
        }
    }

    private boolean isPng(byte[] value) {
        int[] signature = {0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        for (int index = 0; index < signature.length; index++) {
            if (unsigned(value[index]) != signature[index]) {
                return false;
            }
        }
        return "IHDR".equals(new String(value, 12, 4, StandardCharsets.US_ASCII));
    }

    private boolean isWebp(byte[] value) {
        return "RIFF".equals(new String(value, 0, 4, StandardCharsets.US_ASCII))
                && "WEBP".equals(new String(value, 8, 4, StandardCharsets.US_ASCII));
    }

    private int readIntBigEndian(byte[] value, int offset) {
        return unsigned(value[offset]) << 24
                | unsigned(value[offset + 1]) << 16
                | unsigned(value[offset + 2]) << 8
                | unsigned(value[offset + 3]);
    }

    private int read24LittleEndian(byte[] value, int offset) {
        return unsigned(value[offset])
                | unsigned(value[offset + 1]) << 8
                | unsigned(value[offset + 2]) << 16;
    }

    private int unsigned(byte value) {
        return Byte.toUnsignedInt(value);
    }

    private String readType(DataInputStream input) throws IOException {
        return new String(input.readNBytes(4), StandardCharsets.US_ASCII);
    }

    private MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private record DetectedImage(String mimeType, int width, int height) {}

    private static final class StringBuilderOutputStream extends OutputStream {
        private final StringBuilder target;

        private StringBuilderOutputStream(StringBuilder target) {
            this.target = target;
        }

        @Override
        public void write(int value) {
            target.append((char) value);
        }

        @Override
        public void write(byte[] value, int offset, int length) {
            target.append(new String(value, offset, length, StandardCharsets.ISO_8859_1));
        }
    }
}

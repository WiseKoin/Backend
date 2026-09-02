package com.wisekoin.backend.module.receipt;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import com.fasterxml.jackson.databind.ObjectMapper;

class ReceiptServiceTest {

    private ReceiptService receiptService;

    @BeforeEach
    void setUp() {
        receiptService = new ReceiptService(new ObjectMapper());
    }

    @Test
    @DisplayName("Should normalize from image/jpg into image/jpeg")
    void validateMimeType_WhenJpg_ShouldReturnJpeg() {
        MockMultipartFile file = new MockMultipartFile(
            "file", "bill.jpg", "image/jpg", "fake-bytes".getBytes()
        );

        String result = receiptService.validateAndNormalizeMimeType(file);

        assertEquals("image/jpeg", result);
    }

    @Test
    @DisplayName("Nên chấp nhận các định dạng hợp lệ: image/png và image/webp")
    void validateMimeType_WhenPngOrWebp_ShouldReturnExactType() {
        MockMultipartFile pngFile = new MockMultipartFile(
            "file", "bill.png", "image/png", "fake-bytes".getBytes()
        );
        MockMultipartFile webpFile = new MockMultipartFile(
            "file", "bill.webp", "image/webp", "fake-bytes".getBytes()
        );

        assertEquals("image/png", receiptService.validateAndNormalizeMimeType(pngFile));
        assertEquals("image/webp", receiptService.validateAndNormalizeMimeType(webpFile));
    }

    @Test
    @DisplayName("Nên quăng lỗi IllegalArgumentException khi file rỗng hoặc null")
    void validateMimeType_WhenFileEmptyOrNull_ShouldThrowException() {
        MockMultipartFile emptyFile = new MockMultipartFile(
            "file", "empty.jpg", "image/jpeg", new byte[0]
        );

        IllegalArgumentException exceptionForEmpty = assertThrows(
            IllegalArgumentException.class,
            () -> receiptService.validateAndNormalizeMimeType(emptyFile)
        );
        assertEquals("Uploaded file cannot be empty.", exceptionForEmpty.getMessage());

        // Kiểm tra ném ngoại lệ khi truyền null
        assertThrows(
            IllegalArgumentException.class,
            () -> receiptService.validateAndNormalizeMimeType(null)
        );
    }

    @Test
    @DisplayName("Nên quăng lỗi IllegalArgumentException khi định dạng không hỗ trợ (PDF, TXT)")
    void validateMimeType_WhenUnsupportedType_ShouldThrowException() {
        MockMultipartFile pdfFile = new MockMultipartFile(
            "file", "document.pdf", "application/pdf", "fake-bytes".getBytes()
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> receiptService.validateAndNormalizeMimeType(pdfFile)
        );
    }
}
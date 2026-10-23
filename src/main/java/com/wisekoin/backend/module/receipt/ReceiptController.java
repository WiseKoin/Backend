package com.wisekoin.backend.module.receipt;

import com.wisekoin.backend.module.auth.security.CustomUserDetails;
import com.wisekoin.backend.module.common.exception.BadRequestException;
import com.wisekoin.backend.module.receipt.dto.ReceiptResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/receipts")
@RequiredArgsConstructor
public class ReceiptController {
    private final ReceiptService receiptService;

    @PostMapping(value = "/extract", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ReceiptResponse> extractReceiptData(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "locale", required = false) String locale,
            @RequestParam(value = "time_zone", required = false) String timeZone,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal CustomUserDetails currentUser)
            throws IOException {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("File cannot be empty.");
        }

        ReceiptResponse result =
                receiptService.extractReceipt(
                        file, currentUser.getId(), locale, timeZone, idempotencyKey);
        return ResponseEntity.ok(result);
    }
}

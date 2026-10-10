package com.wisekoin.backend.module.voice;

import com.wisekoin.backend.module.auth.security.CustomUserDetails;
import com.wisekoin.backend.module.voice.dto.VoiceResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/voice")
@RequiredArgsConstructor
@Slf4j
public class VoiceController {
    private final VoiceService voiceService;

    @PostMapping(value = "/extract", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> extractVoiceData(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body("File cannot be empty.");
        }

        try {
            VoiceResponse result = voiceService.extractVoice(file, currentUser.getId());
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (IOException e) {
            log.error("Error processing voice audio file", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error when processing audio file: " + e.getMessage());
        } catch (org.springframework.web.client.RestClientResponseException e) {
            log.error(
                    "Gemini API returned error status: {}, body: {}",
                    e.getStatusCode(),
                    e.getResponseBodyAsString(),
                    e);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(
                            "Gemini API Error ("
                                    + e.getStatusCode()
                                    + "): "
                                    + e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("Error during voice extraction processing", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error processing voice request: " + e.getMessage());
        }
    }
}

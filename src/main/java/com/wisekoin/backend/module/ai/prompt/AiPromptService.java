package com.wisekoin.backend.module.ai.prompt;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

@Service
public class AiPromptService {
    private final Resource receiptPromptResource;
    private final String receiptPromptVersion;
    private final Resource voicePromptResource;
    private final String voicePromptVersion;

    public AiPromptService(
            @Value("${app.ai.prompts.receipt.resource:classpath:ai/prompts/receipt-v2.txt}")
                    Resource receiptPromptResource,
            @Value("${app.ai.prompts.receipt.version:receipt-v2}") String receiptPromptVersion,
            @Value("${app.ai.prompts.voice.resource:classpath:ai/prompts/voice-v2.txt}")
                    Resource voicePromptResource,
            @Value("${app.ai.prompts.voice.version:voice-v2}") String voicePromptVersion) {
        this.receiptPromptResource = receiptPromptResource;
        this.receiptPromptVersion = receiptPromptVersion;
        this.voicePromptResource = voicePromptResource;
        this.voicePromptVersion = voicePromptVersion;
    }

    public VersionedPrompt receipt(Map<String, String> values) {
        return render(receiptPromptResource, receiptPromptVersion, values);
    }

    public VersionedPrompt voice(Map<String, String> values) {
        return render(voicePromptResource, voicePromptVersion, values);
    }

    private VersionedPrompt render(Resource resource, String version, Map<String, String> values) {
        try {
            String content = resource.getContentAsString(StandardCharsets.UTF_8);
            for (Map.Entry<String, String> value : values.entrySet()) {
                content =
                        content.replace(
                                "{{" + value.getKey() + "}}",
                                value.getValue() != null ? value.getValue() : "");
            }
            if (content.matches("(?s).*\\{\\{[A-Z0-9_]+}}.*")) {
                throw new IllegalStateException(
                        "AI prompt " + version + " has unresolved template values.");
            }
            return new VersionedPrompt(version, content.trim());
        } catch (IOException exception) {
            throw new IllegalStateException("Could not load AI prompt " + version + ".", exception);
        }
    }
}

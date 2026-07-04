package com.digitalocean.llmproxy.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Normalizes raw LLM text and JSON before shadow comparison.
 */
@Component
public class OutputNormalizer {

    private static final Pattern MARKDOWN_JSON_BLOCK = Pattern.compile(
            "```(?:json)?\\s*([\\s\\S]*?)```",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern TRAILING_PUNCTUATION = Pattern.compile("[\\s.,!?;:\"'\\-–—]+$");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final int PREVIEW_MAX_LENGTH = 100;

    private final ObjectMapper objectMapper;

    public OutputNormalizer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public boolean normalizeAndCompare(String primaryRaw, String candidateRaw) {
        return normalize(primaryRaw).equals(normalize(candidateRaw));
    }

    public String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }

        String text = extractContent(raw);
        text = stripMarkdownJsonBlocks(text);
        text = tryCanonicalizeJson(text);

        if (looksLikeJson(text)) {
            return collapseWhitespace(text);
        }

        text = collapseWhitespace(text);
        return lowercaseAndStripTrailingPunctuation(text);
    }

    public String preview(String raw) {
        if (raw == null) {
            return "";
        }
        String collapsed = collapseWhitespace(raw);
        if (collapsed.length() <= PREVIEW_MAX_LENGTH) {
            return collapsed;
        }
        return collapsed.substring(0, PREVIEW_MAX_LENGTH) + "...";
    }

    public String sha256(String raw) {
        if (raw == null) {
            raw = "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    String extractContent(String raw) {
        String trimmed = raw.trim();
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            return trimmed;
        }
        try {
            JsonNode root = objectMapper.readTree(trimmed);
            JsonNode choices = root.path("choices");
            if (choices.isArray() && !choices.isEmpty()) {
                JsonNode message = choices.get(0).path("message").path("content");
                if (!message.isMissingNode() && message.isTextual()) {
                    return message.asText();
                }
                JsonNode text = choices.get(0).path("text");
                if (!text.isMissingNode() && text.isTextual()) {
                    return text.asText();
                }
            }
            JsonNode content = root.path("content");
            if (!content.isMissingNode() && content.isTextual()) {
                return content.asText();
            }
            return trimmed;
        } catch (JsonProcessingException e) {
            return trimmed;
        }
    }

    String stripMarkdownJsonBlocks(String text) {
        var matcher = MARKDOWN_JSON_BLOCK.matcher(text);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return text.trim();
    }

    String tryCanonicalizeJson(String text) {
        String candidate = text.trim();
        if (!looksLikeJson(candidate)) {
            return candidate;
        }
        try {
            JsonNode node = objectMapper.readTree(candidate);
            return objectMapper.writeValueAsString(sortKeysRecursively(node));
        } catch (JsonProcessingException e) {
            return candidate;
        }
    }

    static boolean looksLikeJson(String text) {
        return (text.startsWith("{") && text.endsWith("}"))
                || (text.startsWith("[") && text.endsWith("]"));
    }

    JsonNode sortKeysRecursively(JsonNode node) {
        if (node.isObject()) {
            ObjectNode sorted = JsonNodeFactory.instance.objectNode();
            List<String> fieldNames = new ArrayList<>();
            node.fieldNames().forEachRemaining(fieldNames::add);
            Collections.sort(fieldNames);
            for (String fieldName : fieldNames) {
                sorted.set(fieldName, sortKeysRecursively(node.get(fieldName)));
            }
            return sorted;
        }
        if (node.isArray()) {
            var array = JsonNodeFactory.instance.arrayNode();
            node.forEach(child -> array.add(sortKeysRecursively(child)));
            return array;
        }
        return node;
    }

    static String collapseWhitespace(String text) {
        return WHITESPACE.matcher(text.trim()).replaceAll(" ");
    }

    static String lowercaseAndStripTrailingPunctuation(String text) {
        String lowered = text.toLowerCase();
        return TRAILING_PUNCTUATION.matcher(lowered).replaceAll("").trim();
    }
}

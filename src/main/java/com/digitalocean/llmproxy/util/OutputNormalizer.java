package com.digitalocean.llmproxy.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Normalizes raw LLM text and JSON before shadow comparison.
 * Strips formatting noise (whitespace, case, markdown blocks, key order) to reduce false mismatches.
 */
public final class OutputNormalizer {

    private static final Pattern MARKDOWN_JSON_BLOCK = Pattern.compile(
            "```(?:json)?\\s*([\\s\\S]*?)```",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern TRAILING_PUNCTUATION = Pattern.compile("[\\s.,!?;:\"'\\-–—]+$");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private static final ObjectMapper CANONICAL_MAPPER = new ObjectMapper()
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

    private OutputNormalizer() {}

    public static boolean normalizeAndCompare(String primaryRaw, String candidateRaw) {
        return normalize(primaryRaw).equals(normalize(candidateRaw));
    }

    public static String normalize(String raw) {
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

    static String extractContent(String raw) {
        String trimmed = raw.trim();
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            return trimmed;
        }
        try {
            JsonNode root = CANONICAL_MAPPER.readTree(trimmed);
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

    static String stripMarkdownJsonBlocks(String text) {
        var matcher = MARKDOWN_JSON_BLOCK.matcher(text);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return text.trim();
    }

    static String tryCanonicalizeJson(String text) {
        String candidate = text.trim();
        if (!looksLikeJson(candidate)) {
            return candidate;
        }
        try {
            JsonNode node = CANONICAL_MAPPER.readTree(candidate);
            return CANONICAL_MAPPER.writeValueAsString(sortKeysRecursively(node));
        } catch (JsonProcessingException e) {
            return candidate;
        }
    }

    static boolean looksLikeJson(String text) {
        return (text.startsWith("{") && text.endsWith("}"))
                || (text.startsWith("[") && text.endsWith("]"));
    }

    static JsonNode sortKeysRecursively(JsonNode node) {
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

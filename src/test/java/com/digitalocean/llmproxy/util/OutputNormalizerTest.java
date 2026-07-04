package com.digitalocean.llmproxy.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link OutputNormalizer} normalization and comparison behavior.
 */
class OutputNormalizerTest {

    private OutputNormalizer normalizer;

    @BeforeEach
    void setUp() {
        normalizer = new OutputNormalizer(new ObjectMapper());
    }

    @Test
    void normalizeAndCompare_treatsEquivalentJsonWithDifferentKeyOrderAsMatch() {
        String a = "{\"b\":2,\"a\":1}";
        String b = "{\"a\":1,\"b\":2}";
        assertTrue(normalizer.normalizeAndCompare(a, b));
    }

    @Test
    void normalizeAndCompare_stripsMarkdownJsonBlocks() {
        String a = "```json\n{\"status\":\"ok\"}\n```";
        String b = "{\"status\":\"ok\"}";
        assertTrue(normalizer.normalizeAndCompare(a, b));
    }

    @Test
    void normalizeAndCompare_collapsesWhitespaceAndIgnoresCase() {
        String a = "Hello   World!!!";
        String b = "hello world";
        assertTrue(normalizer.normalizeAndCompare(a, b));
    }

    @Test
    void normalizeAndCompare_extractsAssistantContentFromChatCompletionEnvelope() {
        String primary = """
                {"choices":[{"message":{"content":"The answer is 42."}}]}
                """;
        String candidate = "The answer is 42.";
        assertTrue(normalizer.normalizeAndCompare(primary, candidate));
    }

    @Test
    void normalizeAndCompare_detectsRealContentDifference() {
        assertFalse(normalizer.normalizeAndCompare("alpha", "beta"));
    }

    @Test
    void normalize_handlesNullAndBlankAsEmpty() {
        assertEquals("", normalizer.normalize(null));
        assertEquals("", normalizer.normalize("   "));
        assertTrue(normalizer.normalizeAndCompare(null, ""));
    }

    @Test
    void normalize_treatsInvalidJsonAsPlainText() {
        String raw = "{not valid json";
        assertEquals("{not valid json", normalizer.normalize(raw).toLowerCase());
    }

    @Test
    void normalize_collapsesInternalNewlinesForPlainText() {
        assertEquals("foo bar", normalizer.normalize("foo\n\nbar"));
    }

    @ParameterizedTest
    @CsvSource({
            "' trailing space ', 'trailing space'",
            "'UPPER', 'upper'"
    })
    void normalize_normalizesFormattingVariants(String input, String expected) {
        assertEquals(expected, normalizer.normalize(input));
    }

    @Test
    void normalizeAndCompare_doesNotFalseMismatchOnPunctuation() {
        assertTrue(normalizer.normalizeAndCompare("Done.", "done"));
        assertTrue(normalizer.normalizeAndCompare("Yes!", "yes"));
    }
}

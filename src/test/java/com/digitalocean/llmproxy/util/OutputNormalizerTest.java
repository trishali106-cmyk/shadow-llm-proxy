package com.digitalocean.llmproxy.util;

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

    @Test
    void normalizeAndCompare_treatsEquivalentJsonWithDifferentKeyOrderAsMatch() {
        String a = "{\"b\":2,\"a\":1}";
        String b = "{\"a\":1,\"b\":2}";
        assertTrue(OutputNormalizer.normalizeAndCompare(a, b));
    }

    @Test
    void normalizeAndCompare_stripsMarkdownJsonBlocks() {
        String a = "```json\n{\"status\":\"ok\"}\n```";
        String b = "{\"status\":\"ok\"}";
        assertTrue(OutputNormalizer.normalizeAndCompare(a, b));
    }

    @Test
    void normalizeAndCompare_collapsesWhitespaceAndIgnoresCase() {
        String a = "Hello   World!!!";
        String b = "hello world";
        assertTrue(OutputNormalizer.normalizeAndCompare(a, b));
    }

    @Test
    void normalizeAndCompare_extractsAssistantContentFromChatCompletionEnvelope() {
        String primary = """
                {"choices":[{"message":{"content":"The answer is 42."}}]}
                """;
        String candidate = "The answer is 42.";
        assertTrue(OutputNormalizer.normalizeAndCompare(primary, candidate));
    }

    @Test
    void normalizeAndCompare_detectsRealContentDifference() {
        assertFalse(OutputNormalizer.normalizeAndCompare("alpha", "beta"));
    }

    @Test
    void normalize_handlesNullAndBlankAsEmpty() {
        assertEquals("", OutputNormalizer.normalize(null));
        assertEquals("", OutputNormalizer.normalize("   "));
        assertTrue(OutputNormalizer.normalizeAndCompare(null, ""));
    }

    @Test
    void normalize_treatsInvalidJsonAsPlainText() {
        String raw = "{not valid json";
        assertEquals("{not valid json", OutputNormalizer.normalize(raw).toLowerCase());
    }

    @Test
    void normalize_collapsesInternalNewlinesForPlainText() {
        assertEquals("foo bar", OutputNormalizer.normalize("foo\n\nbar"));
    }

    @ParameterizedTest
    @CsvSource({
            "' trailing space ', 'trailing space'",
            "'UPPER', 'upper'"
    })
    void normalize_normalizesFormattingVariants(String input, String expected) {
        assertEquals(expected, OutputNormalizer.normalize(input));
    }

    @Test
    void normalizeAndCompare_doesNotFalseMismatchOnPunctuation() {
        assertTrue(OutputNormalizer.normalizeAndCompare("Done.", "done"));
        assertTrue(OutputNormalizer.normalizeAndCompare("Yes!", "yes"));
    }
}

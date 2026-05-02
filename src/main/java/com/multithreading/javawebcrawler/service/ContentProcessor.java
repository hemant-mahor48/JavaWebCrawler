package com.multithreading.javawebcrawler.service;

import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Cleans raw HTML text and extracts meaningful keywords.
 *
 * Steps:
 *  1. Lowercase everything
 *  2. Remove punctuation
 *  3. Split into tokens
 *  4. Remove stop words (the, a, is, ...)
 *  5. Remove very short tokens (< 3 chars)
 */

@Service
public class ContentProcessor {
    // Common English stop words to filter out
    private static final Set<String> STOP_WORDS = Set.of(
            "the", "a", "an", "and", "or", "but", "in", "on", "at",
            "to", "for", "of", "with", "by", "from", "is", "are",
            "was", "were", "be", "been", "has", "have", "had",
            "it", "its", "this", "that", "they", "their", "we",
            "you", "he", "she", "as", "if", "not", "so", "do"
    );

    /**
     * Extracts meaningful keywords from raw page text.
     * This runs inside a crawler thread — no shared mutable state
     * means it's naturally thread-safe.
     */
    public List<String> extractKeywords(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return List.of();
        }

        return Arrays.stream(
                        rawText
                                .toLowerCase()
                                .replaceAll("[^a-z0-9\\s]", " ") // strip punctuation
                                .split("\\s+")                   // split on whitespace
                )
                .filter(word -> word.length() >= 3)          // drop very short words
                .filter(word -> !STOP_WORDS.contains(word))  // drop stop words
                .distinct()
                .limit(200)                                   // cap per page
                .collect(Collectors.toList());
    }
}

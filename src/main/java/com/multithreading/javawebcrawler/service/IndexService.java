package com.multithreading.javawebcrawler.service;

import com.multithreading.javawebcrawler.model.CrawlResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maintains the Inverted Index — the core data structure of any search engine.
 *
 * Structure:
 *   word  →  [url1, url2, url3 ...]
 *
 * KEY CONCEPTS:
 * ─────────────────────────────────────────────────────────────────
 * ConcurrentHashMap   → Thread-safe map. Multiple crawler threads
 *                       can read/write simultaneously without
 *                       explicit synchronization.
 *
 * merge()             → Atomic operation that either inserts a new
 *                       key or merges with the existing value.
 *                       Prevents race conditions when two threads
 *                       discover the same keyword at the same time.
 *
 * Collections.synchronizedList() → The List values inside the map
 *                       also need to be thread-safe for concurrent
 *                       writes.
 * ─────────────────────────────────────────────────────────────────
 */

@Slf4j
@Service
public class IndexService {
    // word → list of URLs containing that word
    private final ConcurrentHashMap<String, List<String>> invertedIndex
            = new ConcurrentHashMap<>();

    // url → full crawl result
    private final ConcurrentHashMap<String, CrawlResult> pageStore
            = new ConcurrentHashMap<>();

    /**
     * Indexes a crawl result.
     * Called by multiple threads simultaneously — must be thread-safe.
     */
    public void index(CrawlResult result) {
        if (!"SUCCESS".equals(result.getStatus())) return;

        // Store the full page result
        pageStore.put(result.getUrl(), result);

        // Build inverted index: for each keyword, record which URL has it
        for (String keyword : result.getKeywords()) {
            invertedIndex.merge(
                    keyword,
                    // new value if key doesn't exist
                    new ArrayList<>(Collections.singletonList(result.getUrl())),
                    // merge function if key already exists
                    (existingUrls, newUrls) -> {
                        existingUrls.addAll(newUrls);
                        return existingUrls;
                    }
            );
        }

        log.info("Indexed: {} → {} keywords", result.getUrl(), result.getKeywords().size());
    }

    /**
     * Searches the inverted index for a query word.
     * Returns all URLs that contain that keyword.
     */
    public List<String> search(String query) {
        return invertedIndex.getOrDefault(
                query.toLowerCase().trim(),
                Collections.emptyList()
        );
    }

    /**
     * Returns the full inverted index (for inspection/debugging).
     */
    public Map<String, List<String>> getFullIndex() {
        return Collections.unmodifiableMap(invertedIndex);
    }

    public int getTotalPagesIndexed() {
        return pageStore.size();
    }

    public int getTotalKeywordsIndexed() {
        return invertedIndex.size();
    }
}

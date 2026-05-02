package com.multithreading.javawebcrawler.controller;

import com.multithreading.javawebcrawler.model.CrawlResult;
import com.multithreading.javawebcrawler.model.SearchResult;
import com.multithreading.javawebcrawler.service.CrawlerService;
import com.multithreading.javawebcrawler.service.IndexService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST API for the crawler and search index.
 *
 * Endpoints:
 *   POST /api/crawl         → start a new crawl session
 *   GET  /api/search?q=     → search the index
 *   GET  /api/index         → view full inverted index
 *   GET  /api/stats         → index statistics
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CrawlerController {

    private final CrawlerService crawlerService;
    private final IndexService indexService;

    /**
     * Start a crawl.
     *
     * POST /api/crawl
     * Body: {
     *   "seedUrls": ["https://example.com"],
     *   "maxPages": 10,
     *   "maxDepth": 2
     * }
     */
    @PostMapping("/crawl")
    public ResponseEntity<List<CrawlResult>> startCrawl(@RequestBody CrawlRequest request) {
        List<CrawlResult> results = crawlerService.startCrawl(
                request.seedUrls(),
                request.maxPages() > 0 ? request.maxPages() : 10,
                request.maxDepth() > 0 ? request.maxDepth() : 2
        );
        return ResponseEntity.ok(results);
    }

    /**
     * Search the inverted index.
     *
     * GET /api/search?q=java
     */
    @GetMapping("/search")
    public ResponseEntity<SearchResult> search(@RequestParam String q) {
        List<String> urls = indexService.search(q);
        return ResponseEntity.ok(
                SearchResult.builder()
                        .query(q)
                        .totalMatches(urls.size())
                        .matchingUrls(urls)
                        .build()
        );
    }

    /**
     * View the full inverted index.
     *
     * GET /api/index
     */
    @GetMapping("/index")
    public ResponseEntity<Map<String, List<String>>> getIndex() {
        return ResponseEntity.ok(indexService.getFullIndex());
    }

    /**
     * Get crawl statistics.
     *
     * GET /api/stats
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Integer>> getStats() {
        return ResponseEntity.ok(Map.of(
                "totalPagesIndexed", indexService.getTotalPagesIndexed(),
                "totalKeywordsIndexed", indexService.getTotalKeywordsIndexed()
        ));
    }

    // Request body record
    public record CrawlRequest(List<String> seedUrls, int maxPages, int maxDepth) {}
}
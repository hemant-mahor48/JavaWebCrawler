package com.multithreading.javawebcrawler.service;

import com.multithreading.javawebcrawler.model.CrawlResult;
import com.multithreading.javawebcrawler.worker.CrawlerWorker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Orchestrates the entire crawl session.
 *
 * KEY CONCEPTS:
 * ─────────────────────────────────────────────────────────────────
 * BlockingQueue       → The URL Frontier. Worker threads call
 *                       .poll() — if empty, they BLOCK instead of
 *                       busy-waiting. This saves CPU.
 *
 * ConcurrentHashMap   → Thread-safe visited set. Uses
 *                       .add() which internally uses putIfAbsent()
 *                       so two threads can't both "discover"
 *                       the same URL simultaneously.
 *
 * AtomicInteger       → Thread-safe counter. .incrementAndGet()
 *                       is a single atomic CPU operation, no
 *                       synchronized block needed.
 *
 * CompletableFuture
 *   .allOf()          → Waits for ALL crawl futures to complete
 *                       before returning, without blocking threads.
 * ─────────────────────────────────────────────────────────────────
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CrawlerService {

    private final CrawlerWorker crawlerWorker;
    private final IndexService indexService;

    /**
     * Starts a crawl from the given seed URLs up to maxPages total.
     *
     * @param seedUrls  starting URLs
     * @param maxPages  maximum number of pages to crawl
     * @param maxDepth  how many link-hops deep to follow
     */
    public List<CrawlResult> startCrawl(List<String> seedUrls, int maxPages, int maxDepth) {

        // --- Thread-safe data structures ---
        BlockingQueue<String> frontier = new LinkedBlockingQueue<>(seedUrls);
        Set<String> visited = ConcurrentHashMap.newKeySet();  // thread-safe Set
        AtomicInteger crawledCount = new AtomicInteger(0);    // safe counter

        List<CompletableFuture<CrawlResult>> futures = new ArrayList<>();
        List<CrawlResult> results = new ArrayList<>();

        log.info("Starting crawl | Seeds: {} | Max pages: {} | Max depth: {}",
                seedUrls.size(), maxPages, maxDepth);

        // Process URLs level by level (BFS approach)
        for (int depth = 0; depth < maxDepth; depth++) {

            if (frontier.isEmpty()) break;

            List<String> currentBatch = new ArrayList<>();
            frontier.drainTo(currentBatch); // grab all current URLs at once

            for (String url : currentBatch) {

                // Stop if we've hit the page limit
                if (crawledCount.get() >= maxPages) break;

                // Skip if already visited — thread-safe with ConcurrentHashMap set
                if (!visited.add(url)) {
                    log.debug("Skipping already visited: {}", url);
                    continue;
                }

                // Increment atomically — no race condition
                crawledCount.incrementAndGet();

                // Launch async crawl — runs on crawler-thread-N from our pool
                CompletableFuture<CrawlResult> future = crawlerWorker.crawl(url)
                        .thenApply(result -> {
                            // This callback also runs on a pool thread
                            indexService.index(result);

                            // Push newly discovered links into the frontier
                            if (result.getOutboundLinks() != null) {
                                result.getOutboundLinks().stream()
                                        .filter(link -> !visited.contains(link))
                                        .forEach(frontier::offer);
                            }

                            return result;
                        });

                futures.add(future);
            }

            // Wait for this entire depth level to finish before going deeper
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            // Collect results from completed futures
            futures.stream()
                    .map(CompletableFuture::join)
                    .forEach(results::add);

            futures.clear();
        }

        log.info("Crawl complete! Pages: {} | Index size: {} keywords",
                results.size(), indexService.getTotalKeywordsIndexed());

        return results;
    }
}
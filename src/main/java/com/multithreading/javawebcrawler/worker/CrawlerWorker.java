package com.multithreading.javawebcrawler.worker;

import com.multithreading.javawebcrawler.model.CrawlResult;
import com.multithreading.javawebcrawler.service.ContentProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class CrawlerWorker {
    /**
     * CrawlerWorker runs in its own thread from the crawlerExecutor pool.
     *
     * KEY CONCEPTS USED:
     * ─────────────────────────────────────────────────────────────────
     * @Async              → Spring runs this method on a pool thread,
     *                       not the caller's thread. Returns immediately.
     *
     * CompletableFuture   → Lets the caller chain callbacks or wait for
     *                       all workers to finish with .allOf()
     *
     * Thread.currentThread().getName() → shows which thread is working,
     *                       great for understanding pool behavior in logs
     * ─────────────────────────────────────────────────────────────────
     */

    private final ContentProcessor contentProcessor;

    private static final int TIMEOUT_MS = 5000;

    /**
     * Fetches and processes a single URL asynchronously.
     * Each call to this method runs on a separate thread
     * from the "crawlerExecutor" pool.
     */

    @Async("crawlerExecutor")
    public CompletableFuture<CrawlResult> crawl(String url){

        String threadName = Thread.currentThread().getName();
        log.info("[{}] Starting crawl → {}", threadName, url);

        try {
            // 1. Fetch the page HTML using Jsoup
            Document doc = Jsoup.connect(url)
                    .timeout(TIMEOUT_MS)
                    .userAgent("Mozilla/5.0 WebCrawlerBot/1.0")
                    .get();

            // 2. Extract all outbound links from <a href="...">
            List<String> links = doc.select("a[href]")
                    .stream()
                    .map(el -> el.absUrl("href"))
                    .filter(href -> href.startsWith("http"))
                    .distinct()
                    .limit(20) // limit per-page to avoid explosion
                    .toList();

            // 3. Clean and tokenize page text
            String rawText = doc.body().text();
            List<String> keywords = contentProcessor.extractKeywords(rawText);

            log.info("[{}] Done → {} | {} links | {} keywords",
                    threadName, url, links.size(), keywords.size());

            // 4. Build and return result
            return CompletableFuture.completedFuture(
                    CrawlResult.builder()
                            .url(url)
                            .title(doc.title())
                            .rawText(rawText)
                            .keywords(keywords)
                            .outboundLinks(links)
                            .crawledAt(LocalDateTime.now())
                            .status("SUCCESS")
                            .build()
            );

        } catch (Exception e) {
            log.error("[{}] Failed → {} | {}", threadName, url, e.getMessage());

            return CompletableFuture.completedFuture(
                    CrawlResult.builder()
                            .url(url)
                            .crawledAt(LocalDateTime.now())
                            .status("FAILED")
                            .errorMessage(e.getMessage())
                            .build()
            );
        }
    }
}

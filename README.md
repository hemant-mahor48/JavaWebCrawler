# 🕷️ Web Crawler & Indexer

A multithreaded web crawler and search indexer built with **Java 21** and **Spring Boot 3.2**.  
Designed as a deep-dive into Java concurrency — every core threading primitive is used in a real, working context.

---

## 📑 Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Multithreading Concepts](#multithreading-concepts)
- [Project Structure](#project-structure)
- [Prerequisites](#prerequisites)
- [Getting Started](#getting-started)
- [API Reference](#api-reference)
- [Configuration](#configuration)
- [How It Works — Step by Step](#how-it-works--step-by-step)
- [Reading the Logs](#reading-the-logs)
- [Extending the Project](#extending-the-project)
- [Troubleshooting](#troubleshooting)

---

## Overview

This project crawls web pages concurrently, extracts keywords from each page, and builds a searchable **inverted index** — the same data structure used by real search engines like Google.

**What it demonstrates:**

- Running tasks in parallel using a managed thread pool
- Coordinating shared state safely across threads
- Non-blocking async execution with `CompletableFuture`
- Producer-consumer URL management with `BlockingQueue`
- Lock-free concurrent reads/writes with `ConcurrentHashMap`

---

## Architecture

```
Seed URLs
    │
    ▼
┌─────────────────────────────────────┐
│         URL Frontier                │
│   BlockingQueue + Visited Set       │  ← thread-safe deduplication
│   (ConcurrentHashMap.newKeySet())   │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│      ThreadPoolTaskExecutor         │
│   core=5  max=20  queue=100         │  ← configurable thread pool
└──────┬─────────────┬────────────────┘
       │             │           │
       ▼             ▼           ▼
  [crawler-1]   [crawler-2]  [crawler-N]   ← @Async worker threads
       │             │           │
       └──────┬──────┘───────────┘
              ▼
    ┌──────────────────┐
    │ Content Processor│  ← tokenize, clean, extract keywords
    └──────────────────┘
              │
              ▼
    ┌──────────────────────────────────┐
    │   Inverted Index                 │
    │   ConcurrentHashMap              │  ← word → [url1, url2, ...]
    │   "java" → [url1, url3]          │
    │   "spring" → [url2, url4]        │
    └──────────────────────────────────┘
              │
              ▼
       REST API (Search)
```

---

## Multithreading Concepts

This project is structured as a progressive tour of Java concurrency. Here is every concept used and exactly where to find it:

| Concept | Class | Purpose |
|---|---|---|
| `@Async` | `CrawlerWorker` | Runs each crawl on a pool thread instead of the caller's thread |
| `CompletableFuture` | `CrawlerWorker`, `CrawlerService` | Non-blocking async result + callback chaining |
| `CompletableFuture.allOf()` | `CrawlerService` | Waits for an entire batch of crawls to finish |
| `ThreadPoolTaskExecutor` | `ThreadPoolConfig` | Manages a fixed pool of reusable crawler threads |
| `BlockingQueue` | `CrawlerService` | URL frontier — threads block when empty instead of spinning |
| `ConcurrentHashMap` | `IndexService`, `CrawlerService` | Thread-safe map for index and visited-URL tracking |
| `.merge()` | `IndexService` | Atomic read-modify-write on the index without locks |
| `AtomicInteger` | `CrawlerService` | Lock-free page counter shared across threads |
| `ConcurrentHashMap.newKeySet()` | `CrawlerService` | Thread-safe Set for visited URL deduplication |

### Why each one matters

**`@Async` + `CompletableFuture`**  
Without `@Async`, crawling 10 URLs takes 10 × (fetch time) sequentially. With it, all 10 run simultaneously across the thread pool. `CompletableFuture` lets you attach `.thenApply()` callbacks that also run on pool threads, keeping the pipeline fully non-blocking.

**`BlockingQueue`**  
The URL frontier is a `LinkedBlockingQueue`. When a worker calls `.poll()` on an empty queue it **blocks** the thread automatically until a new URL arrives — no busy-waiting, no wasted CPU cycles.

**`ConcurrentHashMap` + `.merge()`**  
The inverted index is written by multiple threads simultaneously. `ConcurrentHashMap` partitions its buckets internally so threads rarely contend with each other. The `.merge()` method performs the read-check-write sequence atomically, so two threads discovering the same keyword at the same time cannot corrupt the URL list.

**`AtomicInteger`**  
A plain `int` counter would be broken under concurrent writes (`count++` is three operations: read, increment, write). `AtomicInteger.incrementAndGet()` is a single atomic CPU instruction — no `synchronized` block required.

---

## Project Structure

```
web-crawler/
├── pom.xml
└── src/
    └── main/
        ├── java/com/crawler/
        │   ├── WebCrawlerApplication.java     # Entry point, @EnableAsync
        │   ├── config/
        │   │   └── ThreadPoolConfig.java      # Thread pool bean definition
        │   ├── model/
        │   │   ├── CrawlResult.java           # Per-page crawl output
        │   │   └── SearchResult.java          # API search response
        │   ├── worker/
        │   │   └── CrawlerWorker.java         # @Async fetch + parse
        │   ├── service/
        │   │   ├── CrawlerService.java        # Crawl orchestration (BFS)
        │   │   ├── ContentProcessor.java      # Tokenizer + stop-word filter
        │   │   └── IndexService.java          # Inverted index management
        │   └── controller/
        │       └── CrawlerController.java     # REST endpoints
        └── resources/
            └── application.properties
```

---

## Prerequisites

| Requirement | Version |
|---|---|
| Java | 21 or higher |
| Maven | 3.8 or higher |
| Internet access | Required for live crawling |

Verify your setup:

```bash
java -version
mvn -version
```

---

## Getting Started

### 1. Clone the repository

```bash
git clone https://github.com/your-username/web-crawler.git
cd web-crawler
```

### 2. Build the project

```bash
mvn clean install
```

### 3. Run the application

```bash
mvn spring-boot:run
```

The server starts on `http://localhost:8080`.

---

## API Reference

### `POST /api/crawl` — Start a crawl

Launches a multithreaded crawl starting from the provided seed URLs.

**Request body:**

```json
{
  "seedUrls": ["https://wikipedia.org", "https://example.com"],
  "maxPages": 10,
  "maxDepth": 2
}
```

| Field | Type | Default | Description |
|---|---|---|---|
| `seedUrls` | `string[]` | required | Starting URLs for the crawl |
| `maxPages` | `int` | `10` | Maximum total pages to crawl |
| `maxDepth` | `int` | `2` | How many link-hops deep to follow |

**Example:**

```bash
curl -X POST http://localhost:8080/api/crawl \
  -H "Content-Type: application/json" \
  -d '{
    "seedUrls": ["https://wikipedia.org"],
    "maxPages": 5,
    "maxDepth": 2
  }'
```

**Response:**

```json
[
  {
    "url": "https://wikipedia.org",
    "title": "Wikipedia",
    "keywords": ["free", "encyclopedia", "knowledge", "articles"],
    "outboundLinks": ["https://en.wikipedia.org/wiki/Java", "..."],
    "crawledAt": "2024-01-15T10:30:00",
    "status": "SUCCESS"
  }
]
```

---

### `GET /api/search?q={keyword}` — Search the index

Looks up a keyword in the inverted index and returns all matching URLs.

**Example:**

```bash
curl "http://localhost:8080/api/search?q=java"
```

**Response:**

```json
{
  "query": "java",
  "totalMatches": 3,
  "matchingUrls": [
    "https://wikipedia.org",
    "https://en.wikipedia.org/wiki/Java",
    "https://docs.oracle.com"
  ]
}
```

---

### `GET /api/index` — View full inverted index

Returns the complete word-to-URL mapping built so far.

```bash
curl http://localhost:8080/api/index
```

**Response:**

```json
{
  "java": ["https://wikipedia.org", "https://docs.oracle.com"],
  "spring": ["https://spring.io", "https://baeldung.com"],
  "thread": ["https://docs.oracle.com/threading"]
}
```

---

### `GET /api/stats` — Index statistics

```bash
curl http://localhost:8080/api/stats
```

**Response:**

```json
{
  "totalPagesIndexed": 8,
  "totalKeywordsIndexed": 1423
}
```

---

## Configuration

All tuning options are in `src/main/resources/application.properties`:

```properties
# Server port
server.port=8080

# Log level — set to INFO in production
logging.level.com.crawler=DEBUG

# Async request timeout (ms)
spring.mvc.async.request-timeout=60000
```

### Thread pool tuning (`ThreadPoolConfig.java`)

```java
executor.setCorePoolSize(5);     // threads always alive — increase for more parallelism
executor.setMaxPoolSize(20);     // burst capacity under heavy load
executor.setQueueCapacity(100);  // URLs waiting when all threads are busy
executor.setKeepAliveSeconds(60);// idle threads above core die after 60s
```

**Tuning guide:**

| Scenario | Recommendation |
|---|---|
| CPU-bound processing | `corePoolSize` = number of CPU cores |
| I/O-bound (HTTP fetching) | `corePoolSize` = 2–4× CPU cores |
| Slow external sites | Increase `keepAliveSeconds` |
| High URL volume | Increase `queueCapacity` |

---

## How It Works — Step by Step

```
1. POST /api/crawl received with seedUrls
         │
2. CrawlerService puts seed URLs into BlockingQueue (URL Frontier)
         │
3. For each URL in the frontier:
   ├── Check visited set (ConcurrentHashMap) — skip if already seen
   ├── Add to visited set atomically
   ├── Increment AtomicInteger counter
   └── Call crawlerWorker.crawl(url) — returns immediately (@Async)
         │
4. CrawlerWorker runs on crawler-thread-N (from thread pool):
   ├── Jsoup fetches HTML over HTTP
   ├── Extracts all <a href="..."> links
   └── Calls ContentProcessor.extractKeywords()
         │
5. ContentProcessor (runs in same thread):
   ├── Lowercase + strip punctuation
   ├── Split into tokens
   └── Filter stop words → return keyword list
         │
6. CompletableFuture callback (.thenApply()):
   ├── IndexService.index(result) — writes to ConcurrentHashMap
   └── New outbound links pushed back into BlockingQueue
         │
7. CompletableFuture.allOf() waits for entire depth level
         │
8. Next depth level begins with newly discovered URLs
         │
9. Returns List<CrawlResult> when maxPages or maxDepth reached
```

---

## Reading the Logs

When `logging.level.com.crawler=DEBUG`, you will see each thread announce its work:

```
[crawler-thread-3]  Starting crawl → https://wikipedia.org
[crawler-thread-7]  Starting crawl → https://example.com
[crawler-thread-3]  Done → https://wikipedia.org | 18 links | 203 keywords
[crawler-thread-2]  Starting crawl → https://en.wikipedia.org/wiki/Java
[crawler-thread-7]  Done → https://example.com | 4 links | 31 keywords
[crawler-thread-5]  Starting crawl → https://en.wikipedia.org/wiki/Spring
```

Notice the thread numbers are different and interleaved — this proves the crawls are running **in parallel**, not sequentially.

---

## Extending the Project

### Add a rate limiter (Semaphore)

Prevent overwhelming target servers by limiting concurrent requests:

```java
// In CrawlerWorker
private final Semaphore rateLimiter = new Semaphore(3); // max 3 concurrent requests

public CompletableFuture<CrawlResult> crawl(String url) {
    rateLimiter.acquire();
    try {
        // ... fetch page
    } finally {
        rateLimiter.release();
    }
}
```

### Add crawl depth tracking (ThreadLocal)

Track how deep each thread is without passing depth as a parameter:

```java
private static final ThreadLocal<Integer> currentDepth = new ThreadLocal<>();

// Set before crawl
currentDepth.set(depth);

// Read anywhere in the same thread
log.debug("Thread depth: {}", currentDepth.get());
```

### Add retry logic (CountDownLatch)

Wait for all retries to complete before reporting failure:

```java
CountDownLatch latch = new CountDownLatch(retryCount);
// ... retry attempts decrement the latch
latch.await(30, TimeUnit.SECONDS);
```

### Persist the index (Spring Data JPA)

Replace the in-memory `ConcurrentHashMap` with a database-backed store to survive restarts.

---

## Troubleshooting

**`Connection refused` on crawl**  
Some sites block automated requests. Try adding a realistic `User-Agent` header or test with `https://example.com` first.

**Crawl takes very long**  
Reduce `maxPages` and `maxDepth`. Set `maxDepth=1` and `maxPages=3` for quick testing.

**`RejectedExecutionException` in logs**  
The thread pool queue is full. Increase `queueCapacity` in `ThreadPoolConfig` or reduce `maxPages`.

**Index is empty after crawl**  
Check that crawl results have `"status": "SUCCESS"`. Failed pages are not indexed. Enable `DEBUG` logging to see per-URL errors.

---

## License

MIT License — free to use, modify, and distribute.

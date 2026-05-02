package com.multithreading.javawebcrawler.model;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class CrawlResult {
    private String url;
    private String title;
    private String rawText;           // cleaned page content
    private List<String> keywords;    // extracted tokens
    private List<String> outboundLinks; // all <a href> found on page
    private LocalDateTime crawledAt;
    private String status;            // SUCCESS / FAILED
    private String errorMessage;      // null if success
}

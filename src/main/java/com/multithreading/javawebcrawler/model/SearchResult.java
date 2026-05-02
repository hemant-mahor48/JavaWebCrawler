package com.multithreading.javawebcrawler.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class SearchResult {
    private String query;
    private int totalMatches;
    private List<String> matchingUrls;

    // word → list of URLs that contain it
    private Map<String, List<String>> indexSnapshot;
}

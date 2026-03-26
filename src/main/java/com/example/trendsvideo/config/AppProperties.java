package com.example.trendsvideo.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {
    private Rss rss;
    private Trends trends;
    private Article article;
    private Ranking ranking;
    private Video video;
    private Portal portal;

    @Data
    public static class Rss {
        private List<String> feeds;
    }

    @Data
    public static class Trends {
        private boolean enabled;
        private String url;
    }

    @Data
    public static class Article {
        private int maxPerFeed;
    }

    @Data
    public static class Ranking {
        private int topTopics;
    }

    @Data
    public static class Video {
        private String workspace;
        private String outputDir;
        private String ffmpegPath;
    }

    @Data
    public static class Portal {
        private String uploadUrl;
        private String apiKey;
    }
}
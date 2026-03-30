package com.agent.video.generator.trendsvideo.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private Rss rss;
    private Article article;
    private Ranking ranking;
    private Video video;
    private Portal portal;
    private Ollama ollama;
    private Unsplash unsplash;
    private Edgetts edgetts;

    @Data
    public static class Rss {
        private List<String> feeds;
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
        private String outputDir;
        private String workspaceDir;
        private String ffmpegPath;
    }

    @Data
    public static class Portal {
        private String uploadUrl;
        private String apiKey;
    }

    @Data
    public static class Ollama {
        private String baseUrl;
        private String model;
        private int timeoutSeconds;
    }

    @Data
    public static class Unsplash {
        private String accessKey;
    }

    @Data
    public static class Edgetts {
        private String voice;
        private String rate;
        private String volume;
        private String executablePath;
    }
}
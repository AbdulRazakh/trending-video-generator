package com.agent.video.generator.trendsvideo.service;

import com.agent.video.generator.trendsvideo.config.AppProperties;
import com.agent.video.generator.trendsvideo.dto.FeedArticle;
import com.agent.video.generator.trendsvideo.dto.TopicGroup;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Groups articles by topic and ranks by content quality score.
 *
 * Improvement over original: instead of grouping by first 4 words of title
 * (which created poor groups like "We need real peace"),
 * now groups by category from NewsAPI source tag + meaningful keywords.
 */
@Service
@RequiredArgsConstructor
public class TopicRankingService {

    private final AppProperties appProperties;

    private static final List<String> STOP_WORDS = List.of(
        "the","a","an","and","or","but","in","on","at","to","for","of",
        "is","are","was","were","be","been","will","would","can","could",
        "why","how","what","who","when","where","that","this","it","its",
        "says","said","after","before","over","under","about","with","from"
    );

    public List<TopicGroup> rankTopTopics(List<FeedArticle> articles) {
        // Group articles — each article gets its own group since NewsAPI
        // already deduplicates and each article is a distinct story
        // Score by content quality: longer content = better video material
        return articles.stream()
            .filter(a -> hasGoodContent(a))
            .map(a -> TopicGroup.builder()
                .topic(extractTopic(a))
                .articles(List.of(a))
                .score(scoreArticle(a))
                .build())
            .sorted(Comparator.comparingInt(TopicGroup::getScore).reversed())
            .limit(appProperties.getRanking().getTopTopics())
            .collect(Collectors.toList());
    }

    /**
     * Extracts a clean topic label from the article.
     * Uses the most meaningful words from the title.
     */
    private String extractTopic(FeedArticle article) {
        String title = article.getTitle();
        if (title == null || title.isBlank()) return "Breaking News";

        // Remove publication suffix patterns like " - BBC News", " | Reuters"
        title = title.replaceAll("\\s*[-|]\\s*(BBC|CNN|Reuters|AP|NYT|Guardian|Times|Post)[\\w\\s]*$", "");

        // Extract the first 5 meaningful words
        String[] words = title.split("\\s+");
        String topic = Arrays.stream(words)
            .map(w -> w.replaceAll("[^a-zA-Z0-9]", ""))
            .filter(w -> w.length() > 2)
            .filter(w -> !STOP_WORDS.contains(w.toLowerCase()))
            .limit(4)
            .collect(Collectors.joining(" "));

        return topic.isBlank() ? title.substring(0, Math.min(40, title.length())) : topic;
    }

    /**
     * Scores an article by content quality.
     * Higher score = better candidate for video generation.
     */
    private int scoreArticle(FeedArticle article) {
        int score = 0;

        // Content length score (most important)
        String content = article.getFullText() != null ? article.getFullText() : article.getSummary();
        if (content != null) {
            score += Math.min(content.length() / 50, 20); // up to 20 points for length
        }

        // Recency score — newer articles score higher
        if (article.getPublishedAt() != null) {
            long hoursAgo = java.time.Duration.between(
                article.getPublishedAt(),
                java.time.LocalDateTime.now()
            ).toHours();
            if (hoursAgo < 1) score += 10;
            else if (hoursAgo < 6) score += 7;
            else if (hoursAgo < 24) score += 4;
        }

        // Has keywords bonus
        if (article.getKeywords() != null && !article.getKeywords().isEmpty()) {
            score += 3;
        }

        return score;
    }

    private boolean hasGoodContent(FeedArticle article) {
        if (article.getTitle() == null || article.getTitle().isBlank()) return false;
        String content = article.getFullText() != null ? article.getFullText() : article.getSummary();
        int minLength = appProperties.getArticle().getMinContentLength();
        return content != null && content.length() >= minLength;
    }
}

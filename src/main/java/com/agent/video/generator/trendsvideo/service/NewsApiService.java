package com.agent.video.generator.trendsvideo.service;

import com.agent.video.generator.trendsvideo.config.AppProperties;
import com.agent.video.generator.trendsvideo.dto.FeedArticle;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Replaces RssFeedService.
 * Fetches full articles from NewsAPI.org — structured data, no nav menu garbage.
 * Free tier: 100 requests/day, returns title + description + content + source.
 *
 * Sign up free at: https://newsapi.org
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NewsApiService {

    private final AppProperties appProperties;
    private final RestTemplate restTemplate;

    public List<FeedArticle> fetchArticles() {
        List<FeedArticle> allArticles = new ArrayList<>();
        AppProperties.Newsapi cfg = appProperties.getNewsapi();

        if (cfg.getApiKey() == null || cfg.getApiKey().startsWith("YOUR_")) {
            log.warn("NewsAPI key not configured — falling back to empty article list");
            return allArticles;
        }

        String[] categories = cfg.getCategories().split(",");
        for (String category : categories) {
            try {
                List<FeedArticle> articles = fetchByCategory(category.trim(), cfg);
                allArticles.addAll(articles);
                log.info("NewsAPI fetched {} articles for category: {}", articles.size(), category);
            } catch (Exception e) {
                log.error("NewsAPI failed for category {}: {}", category, e.getMessage());
            }
        }

        // Deduplicate by title similarity
        List<FeedArticle> deduplicated = deduplicateArticles(allArticles);
        log.info("Total articles after dedup: {}", deduplicated.size());
        return deduplicated;
    }

    private List<FeedArticle> fetchByCategory(String category, AppProperties.Newsapi cfg) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Api-Key", cfg.getApiKey());
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        String url = cfg.getBaseUrl() + "/top-headlines"
                + "?category=" + category
                + "&language=" + cfg.getLanguage()
                + "&pageSize=" + cfg.getPageSize();

        ResponseEntity<Map> response = restTemplate.exchange(
                url, HttpMethod.GET,
                new HttpEntity<>(headers), Map.class
        );

        Map body = response.getBody();
        if (body == null || !"ok".equals(body.get("status"))) {
            log.warn("NewsAPI returned non-ok status for category: {}", category);
            return List.of();
        }

        List<Map<String, Object>> articles = (List<Map<String, Object>>) body.get("articles");
        if (articles == null) return List.of();

        int minLength = appProperties.getArticle().getMinContentLength();

        return articles.stream()
                .filter(a -> a.get("title") != null)
                .filter(a -> !"[Removed]".equals(a.get("title")))
                .filter(a -> hasEnoughContent(a, minLength))
                .map(a -> buildFeedArticle(a, category))
                .collect(Collectors.toList());
    }

    private boolean hasEnoughContent(Map<String, Object> article, int minLength) {
        String description = (String) article.get("description");
        String content = (String) article.get("content");
        String best = content != null && content.length() > (description != null ? description.length() : 0)
                ? content : description;
        return best != null && best.length() >= minLength;
    }

    private FeedArticle buildFeedArticle(Map<String, Object> raw, String category) {
        String title = cleanString((String) raw.get("title"));
        String description = cleanString((String) raw.get("description"));
        String content = cleanString((String) raw.get("content"));
        String url = (String) raw.get("url");
        String publishedAt = (String) raw.get("publishedAt");

        // NewsAPI content field is truncated with "[+XXXX chars]" — use description as main body
        // and keep content for supplementary info
        String fullText = buildFullText(title, description, content);

        Map<String, Object> source = (Map<String, Object>) raw.get("source");
        String sourceName = source != null ? (String) source.get("name") : "NewsAPI";

        LocalDateTime pubDate = parseDate(publishedAt);

        return FeedArticle.builder()
                .source(sourceName + " [" + category + "]")
                .title(title)
                .link(url != null ? url : "")
                .summary(description != null ? description : "")
                .fullText(fullText)
                .publishedAt(pubDate)
                .keywords(extractTitleKeywords(title))
                .build();
    }

    /**
     * Builds a clean full text from NewsAPI fields.
     * NewsAPI truncates content at ~200 chars — combine description + content for best coverage.
     */
    private String buildFullText(String title, String description, String content) {
        StringBuilder sb = new StringBuilder();

        if (description != null && !description.isBlank()) {
            sb.append(description.trim());
        }

        if (content != null && !content.isBlank()) {
            // Remove the "[+XXXX chars]" truncation marker NewsAPI adds
            String cleanContent = content.replaceAll("\\[\\+\\d+ chars\\]$", "").trim();
            // Only add if it adds new information beyond description
            if (!cleanContent.equals(description) && cleanContent.length() > 50) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(cleanContent);
            }
        }

        return sb.toString().trim();
    }

    private String cleanString(String s) {
        if (s == null) return null;
        return s.replaceAll("\\s+", " ").trim();
    }

    private LocalDateTime parseDate(String publishedAt) {
        if (publishedAt == null) return LocalDateTime.now();
        try {
            return LocalDateTime.parse(publishedAt, DateTimeFormatter.ISO_DATE_TIME);
        } catch (Exception e) {
            return LocalDateTime.now();
        }
    }

    private List<String> extractTitleKeywords(String title) {
        if (title == null) return List.of();
        List<String> stopWords = List.of("the","a","an","and","or","in","on","at","to","for","of","is","are","was");
        return Arrays.stream(title.split("\\s+"))
                .map(w -> w.replaceAll("[^a-zA-Z]", "").toLowerCase())
                .filter(w -> w.length() > 3 && !stopWords.contains(w))
                .distinct()
                .limit(4)
                .collect(Collectors.toList());
    }

    /**
     * Removes duplicate articles based on title similarity.
     * NewsAPI sometimes returns the same story from multiple sources.
     */
    private List<FeedArticle> deduplicateArticles(List<FeedArticle> articles) {
        List<FeedArticle> result = new ArrayList<>();
        Set<String> seenTitles = new HashSet<>();

        for (FeedArticle article : articles) {
            // Normalize title — first 40 chars, lowercase, letters only
            String normalizedTitle = article.getTitle()
                    .toLowerCase()
                    .replaceAll("[^a-z0-9]", "")
                    .substring(0, Math.min(40, article.getTitle().replaceAll("[^a-z0-9]", "").length()));

            if (!seenTitles.contains(normalizedTitle)) {
                seenTitles.add(normalizedTitle);
                result.add(article);
            }
        }
        return result;
    }
}

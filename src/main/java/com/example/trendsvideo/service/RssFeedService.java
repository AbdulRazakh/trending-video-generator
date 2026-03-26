package com.example.trendsvideo.service;

import com.example.trendsvideo.config.AppProperties;
import com.example.trendsvideo.dto.FeedArticle;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class RssFeedService {

    private final AppProperties appProperties;

    public List<FeedArticle> fetchArticles() {
        List<FeedArticle> results = new ArrayList<>();

        for (String feedUrl : appProperties.getRss().getFeeds()) {
            try {
                List<FeedArticle> feedArticles = readFeed(feedUrl);
                results.addAll(feedArticles);
                log.info("Loaded {} articles from {}", feedArticles.size(), feedUrl);
            } catch (Exception e) {
                log.error("Failed to read feed: {} error={}", feedUrl, e.getMessage());
            }
        }

        return results;
    }

    private List<FeedArticle> readFeed(String feedUrl) throws Exception {
        List<FeedArticle> articles = new ArrayList<>();

        HttpURLConnection connection = (HttpURLConnection) new URL(feedUrl).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);
        connection.setRequestProperty("User-Agent", "Mozilla/5.0");
        connection.setInstanceFollowRedirects(true);

        try (InputStream inputStream = connection.getInputStream();
             XmlReader reader = new XmlReader(inputStream)) {

            SyndFeed feed = new SyndFeedInput().build(reader);

            int count = 0;
            for (SyndEntry entry : feed.getEntries()) {
                if (count >= appProperties.getArticle().getMaxPerFeed()) {
                    break;
                }

                FeedArticle article = FeedArticle.builder()
                        .source(feed.getTitle() != null ? feed.getTitle() : feedUrl)
                        .title(entry.getTitle())
                        .link(entry.getLink())
                        .summary(entry.getDescription() != null ? entry.getDescription().getValue() : "")
                        .publishedAt(entry.getPublishedDate() != null
                                ? LocalDateTime.ofInstant(entry.getPublishedDate().toInstant(), ZoneId.systemDefault())
                                : LocalDateTime.now())
                        .build();

                articles.add(article);
                count++;
            }
        }

        return articles;
    }
}
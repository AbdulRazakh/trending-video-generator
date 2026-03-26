package com.example.trendsvideo.service;

import com.example.trendsvideo.config.AppProperties;
import com.example.trendsvideo.dto.FeedArticle;
import com.example.trendsvideo.dto.TopicGroup;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TopicRankingService {

    private final AppProperties appProperties;

    public List<TopicGroup> rankTopTopics(List<FeedArticle> articles) {
        Map<String, List<FeedArticle>> grouped = new HashMap<>();

        for (FeedArticle article : articles) {
            String topic = normalizeTopic(article.getTitle());
            grouped.computeIfAbsent(topic, k -> new ArrayList<>()).add(article);
        }

        return grouped.entrySet().stream()
                .map(e -> TopicGroup.builder()
                        .topic(e.getKey())
                        .articles(e.getValue())
                        .score(e.getValue().size())
                        .build())
                .sorted(Comparator.comparingInt(TopicGroup::getScore).reversed())
                .limit(appProperties.getRanking().getTopTopics())
                .collect(Collectors.toList());
    }

    private String normalizeTopic(String title) {
        if (title == null || title.isBlank()) return "General News";
        String cleaned = title.replaceAll("[^a-zA-Z0-9\\s]", "").trim();
        String[] words = cleaned.split("\\s+");
        return Arrays.stream(words).limit(4).collect(Collectors.joining(" "));
    }
}
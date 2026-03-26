package com.agent.video.generator.trendsvideo.service;

import com.agent.video.generator.trendsvideo.dto.FeedArticle;
import com.agent.video.generator.trendsvideo.dto.TopicGroup;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.stream.Collectors;

@Service
public class StoryMergeService {

    public String mergeToNarrative(TopicGroup topicGroup) {
        return topicGroup.getArticles().stream()
                .sorted(Comparator.comparing(FeedArticle::getPublishedAt).reversed())
                .map(a -> "Source: " + a.getSource() + "\nTitle: " + a.getTitle() + "\nSummary: " + safe(a.getFullText()))
                .collect(Collectors.joining("\n\n---\n\n"));
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }
}
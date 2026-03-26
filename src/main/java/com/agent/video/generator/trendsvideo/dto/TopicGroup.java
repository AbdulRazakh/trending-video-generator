package com.agent.video.generator.trendsvideo.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class TopicGroup {
    private String topic;
    private int score;
    private List<FeedArticle> articles;
}
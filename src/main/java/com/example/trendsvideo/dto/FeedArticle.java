package com.example.trendsvideo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedArticle {
    private String source;
    private String title;
    private String link;
    private String summary;
    private String fullText;
    private LocalDateTime publishedAt;
    private List<String> keywords;
}
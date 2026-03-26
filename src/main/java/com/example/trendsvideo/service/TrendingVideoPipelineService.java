package com.example.trendsvideo.service;

import com.example.trendsvideo.dto.FeedArticle;
import com.example.trendsvideo.dto.PortalUploadResponse;
import com.example.trendsvideo.dto.TopicGroup;
import com.example.trendsvideo.dto.VideoScriptJson;
import com.example.trendsvideo.entity.GeneratedVideo;
import com.example.trendsvideo.repository.GeneratedVideoRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TrendingVideoPipelineService {

    private final RssFeedService rssFeedService;
    private final ArticleExtractorService articleExtractorService;
    private final TopicRankingService topicRankingService;
    private final StoryMergeService storyMergeService;
    private final ScriptGeneratorService scriptGeneratorService;
    private final ScriptValidationService scriptValidationService;
    private final VideoGenerationService videoGenerationService;
    private final VideoValidationService videoValidationService;
    private final PortalUploadService portalUploadService;
    private final GeneratedVideoRepository generatedVideoRepository;
    private final ObjectMapper objectMapper;

    public void runPipeline() throws Exception {
        List<FeedArticle> rawArticles = rssFeedService.fetchArticles();

        List<FeedArticle> enriched = rawArticles.stream()
                .map(articleExtractorService::enrich)
                .collect(Collectors.toList());

        List<TopicGroup> topTopics = topicRankingService.rankTopTopics(enriched);

        for (TopicGroup topic : topTopics) {
            String mergedStory = storyMergeService.mergeToNarrative(topic);

            VideoScriptJson script = scriptGeneratorService.generateEnglishScript(topic, mergedStory);
            scriptValidationService.validate(script);

            String videoPath = videoGenerationService.generateVideo(script);
            videoValidationService.validate(videoPath);

            PortalUploadResponse uploadResponse = portalUploadService.uploadVideo(
                    new File(videoPath),
                    script.getTitle()
            );

            GeneratedVideo generatedVideo = new GeneratedVideo();
            generatedVideo.setTopicTitle(script.getTitle());
            generatedVideo.setScriptJson(objectMapper.writeValueAsString(script));
            generatedVideo.setVideoPath(videoPath);
            generatedVideo.setUploadStatus(uploadResponse != null ? uploadResponse.getStatus() : "FAILED");
            generatedVideo.setPortalVideoId(uploadResponse != null ? uploadResponse.getVideoId() : null);
            generatedVideo.setPortalUrl(uploadResponse != null ? uploadResponse.getUrl() : null);
            generatedVideo.setCreatedAt(LocalDateTime.now());

            generatedVideoRepository.save(generatedVideo);
        }
    }
}
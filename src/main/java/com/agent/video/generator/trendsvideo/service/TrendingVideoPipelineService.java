package com.agent.video.generator.trendsvideo.service;

import com.agent.video.generator.trendsvideo.dto.FeedArticle;
import com.agent.video.generator.trendsvideo.dto.PortalUploadResponse;
import com.agent.video.generator.trendsvideo.dto.TopicGroup;
import com.agent.video.generator.trendsvideo.dto.VideoScriptJson;
import com.agent.video.generator.trendsvideo.entity.GeneratedVideo;
import com.agent.video.generator.trendsvideo.repository.GeneratedVideoRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TrendingVideoPipelineService {

    private final RssFeedService rssFeedService;
    private final ArticleExtractorService articleExtractorService;
    private final TopicRankingService topicRankingService;
    private final StoryMergeService storyMergeService;
    private final ScriptGeneratorService scriptGeneratorService;
    private final ScriptValidationService scriptValidationService;
    private final SceneAssemblyService sceneAssemblyService;
    private final VideoAssemblyService videoAssemblyService;
    private final VideoValidationService videoValidationService;
    private final PortalUploadService portalUploadService;
    private final GeneratedVideoRepository generatedVideoRepository;
    private final ObjectMapper objectMapper;

    public void runPipeline() throws Exception {
        log.info("Pipeline started");

        List<FeedArticle> rawArticles = rssFeedService.fetchArticles();
        log.info("Fetched {} raw articles", rawArticles.size());

        List<FeedArticle> enriched = rawArticles.stream()
                .map(articleExtractorService::enrich)
                .collect(Collectors.toList());

        List<TopicGroup> topTopics = topicRankingService.rankTopTopics(enriched);
        log.info("Processing {} top topics", topTopics.size());

        for (TopicGroup topic : topTopics) {
            try {
                log.info("--- Topic: {} ---", topic.getTopic());
                String mergedStory = storyMergeService.mergeToNarrative(topic);

                VideoScriptJson script = scriptGeneratorService.generateEnglishScript(topic, mergedStory);
                scriptValidationService.validate(script);

                List<String> sceneClips = sceneAssemblyService.assembleSceneClips(script);
                String videoPath = videoAssemblyService.concatenateScenes(sceneClips);
                videoValidationService.validate(videoPath);

//                PortalUploadResponse uploadResponse = portalUploadService.uploadVideo(
//                        new File(videoPath), script.getTitle());
//
//                GeneratedVideo generatedVideo = new GeneratedVideo();
//                generatedVideo.setTopicTitle(script.getTitle());
//                generatedVideo.setScriptJson(objectMapper.writeValueAsString(script));
//                generatedVideo.setVideoPath(videoPath);
//                generatedVideo.setUploadStatus(uploadResponse != null ? uploadResponse.getStatus() : "FAILED");
//                generatedVideo.setPortalVideoId(uploadResponse != null ? uploadResponse.getVideoId() : null);
//                generatedVideo.setPortalUrl(uploadResponse != null ? uploadResponse.getUrl() : null);
//                generatedVideo.setCreatedAt(LocalDateTime.now());
//                generatedVideoRepository.save(generatedVideo);

                log.info("Topic complete: {} -> {}", topic.getTopic(), videoPath);

            } catch (Exception e) {
                log.error("Failed to process topic '{}': {}", topic.getTopic(), e.getMessage(), e);
                // Continue with next topic instead of aborting entire pipeline
            }
        }

        log.info("Pipeline complete");
    }
}
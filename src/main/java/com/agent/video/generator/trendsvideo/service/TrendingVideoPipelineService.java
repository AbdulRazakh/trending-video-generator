package com.agent.video.generator.trendsvideo.service;

import com.agent.video.generator.trendsvideo.dto.FeedArticle;
import com.agent.video.generator.trendsvideo.dto.TopicGroup;
import com.agent.video.generator.trendsvideo.dto.VideoScriptJson;
import com.agent.video.generator.trendsvideo.entity.GeneratedVideo;
import com.agent.video.generator.trendsvideo.repository.GeneratedVideoRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TrendingVideoPipelineService {

    private final NewsApiService newsApiService;           // replaces RssFeedService
    private final TopicRankingService topicRankingService;
    private final StoryMergeService storyMergeService;
    private final ScriptGeneratorService scriptGeneratorService;
    private final ScriptValidationService scriptValidationService;
    private final SceneAssemblyService sceneAssemblyService;
    private final VideoAssemblyService videoAssemblyService;
    private final VideoValidationService videoValidationService;
    private final GeneratedVideoRepository generatedVideoRepository;
    private final ObjectMapper objectMapper;

    public void runPipeline() throws Exception {
        log.info("Pipeline started");

        // Step 1: Fetch articles from NewsAPI (structured, clean content)
        List<FeedArticle> articles = newsApiService.fetchArticles();
        log.info("Fetched {} articles from NewsAPI", articles.size());

        if (articles.isEmpty()) {
            log.warn("No articles fetched — check NewsAPI key in application.yml");
            return;
        }

        // Step 2: Rank and group top topics
        List<TopicGroup> topTopics = topicRankingService.rankTopTopics(articles);
        log.info("Processing {} top topics", topTopics.size());

        for (TopicGroup topic : topTopics) {
            try {
                log.info("--- Topic: {} ---", topic.getTopic());
                String mergedStory = storyMergeService.mergeToNarrative(topic);

                // Step 3: Generate script via Groq (primary) or Ollama (fallback)
                VideoScriptJson script = scriptGeneratorService.generateEnglishScript(topic, mergedStory);
                scriptValidationService.validate(script);
                log.info("Script: '{}' ({} scenes)", script.getTitle(), script.getScenes().size());

                // Step 4: Assemble scene clips (TTS + Unsplash + FFmpeg Ken Burns)
                List<String> sceneClips = sceneAssemblyService.assembleSceneClips(script);

                // Step 5: Concatenate all scenes into final MP4
                String videoPath = videoAssemblyService.concatenateScenes(sceneClips);
                videoValidationService.validate(videoPath);

                // Step 6: Persist to DB
                GeneratedVideo record = new GeneratedVideo();
                record.setTopicTitle(script.getTitle());
                record.setScriptJson(objectMapper.writeValueAsString(script));
                record.setVideoPath(videoPath);
                record.setUploadStatus("LOCAL");
                record.setCreatedAt(LocalDateTime.now());
                generatedVideoRepository.save(record);

                log.info("Topic complete: '{}' -> {}", topic.getTopic(), videoPath);

            } catch (Exception e) {
                log.error("Failed to process topic '{}': {}", topic.getTopic(), e.getMessage(), e);
                // Continue with next topic
            }
        }

        log.info("Pipeline complete");
    }
}

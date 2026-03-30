package com.agent.video.generator.trendsvideo.service;

import com.agent.video.generator.trendsvideo.config.AppProperties;
import com.agent.video.generator.trendsvideo.dto.TopicGroup;
import com.agent.video.generator.trendsvideo.dto.VideoScriptJson;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScriptGeneratorService {

    private final AppProperties appProperties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public VideoScriptJson generateEnglishScript(TopicGroup topic, String mergedStory) {
        String prompt = buildPrompt(topic.getTopic(), mergedStory);
        log.info("Calling Ollama for script on topic: {}", topic.getTopic());

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> requestBody = Map.of(
                    "model", appProperties.getOllama().getModel(),
                    "prompt", prompt,
                    "stream", false,
                    "options", Map.of(
                            "temperature", 0.7,
                            "num_predict", 1200
                    )
            );

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    appProperties.getOllama().getBaseUrl() + "/api/generate",
                    HttpMethod.POST,
                    entity,
                    Map.class
            );

            String rawText = (String) response.getBody().get("response");
            String cleanedJson = extractJson(rawText);
            VideoScriptJson script = objectMapper.readValue(cleanedJson, VideoScriptJson.class);
            log.info("Ollama script generated successfully for: {}", topic.getTopic());
            return script;

        } catch (Exception e) {
            log.error("Ollama call failed, using fallback script: {}", e.getMessage());
            return buildFallbackScript(topic.getTopic(), mergedStory);
        }
    }

    private String extractJson(String raw) {
        // Strip any markdown fences or leading text before the JSON
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start == -1 || end == -1) {
            throw new RuntimeException("No JSON found in Ollama response");
        }
        return raw.substring(start, end + 1);
    }

    private String buildPrompt(String topic, String mergedStory) {
        String truncatedStory = mergedStory.length() > 2000
                ? mergedStory.substring(0, 2000) : mergedStory;

        return """
            You are a professional news video scriptwriter. Generate a short video script as JSON only.

            Topic: %s

            Source material:
            %s

            Return ONLY a valid JSON object, no explanation, no markdown fences. Use this exact structure:
            {
              "title": "short punchy title",
              "language": "en",
              "narrationStyle": "professional news anchor",
              "scenes": [
                {
                  "heading": "Hook",
                  "narration": "7-9 seconds of spoken narration text here",
                  "visualPrompt": "slow pan across city skyline at night, dramatic lighting",
                  "durationSeconds": 8,
                  "imageHints": ["city", "night"]
                },
                {
                  "heading": "Key facts",
                  "narration": "15-20 seconds of spoken narration covering 3 key facts",
                  "visualPrompt": "close-up of newspaper headlines, people reading news",
                  "durationSeconds": 18,
                  "imageHints": ["%s", "news"]
                },
                {
                  "heading": "Closing",
                  "narration": "5-7 seconds closing narration with call to action",
                  "visualPrompt": "sunrise over city, hopeful mood, wide angle shot",
                  "durationSeconds": 6,
                  "imageHints": ["follow", "update"]
                }
              ]
            }
            """.formatted(topic, truncatedStory, topic.split(" ")[0]);
    }

    private VideoScriptJson buildFallbackScript(String topic, String story) {
        VideoScriptJson script = new VideoScriptJson();
        script.setTitle("Breaking: " + topic);
        script.setLanguage("en");
        script.setNarrationStyle("professional news anchor");

        VideoScriptJson.SceneJson intro = new VideoScriptJson.SceneJson();
        intro.setHeading("Hook");
        intro.setNarration("Here is what you need to know about " + topic + " right now.");
        intro.setVisualPrompt("dramatic news studio opening, bright lights, camera zoom in");
        intro.setDurationSeconds(8);
        intro.setImageHints(List.of(topic, "news"));

        VideoScriptJson.SceneJson main = new VideoScriptJson.SceneJson();
        main.setHeading("Key facts");
        String narration = story.length() > 400 ? story.substring(0, 400) : story;
        main.setNarration(narration);
        main.setVisualPrompt("people discussing important news, city background, documentary style");
        main.setDurationSeconds(18);
        main.setImageHints(List.of(topic));

        VideoScriptJson.SceneJson outro = new VideoScriptJson.SceneJson();
        outro.setHeading("Closing");
        outro.setNarration("Stay informed and follow us for the latest updates on this story.");
        outro.setVisualPrompt("sunrise over city, optimistic wide shot, lens flare");
        outro.setDurationSeconds(6);
        outro.setImageHints(List.of("news", "update"));

        script.setScenes(List.of(intro, main, outro));
        return script;
    }
}
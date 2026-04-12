package com.agent.video.generator.trendsvideo.service;

import com.agent.video.generator.trendsvideo.config.AppProperties;
import com.agent.video.generator.trendsvideo.dto.FeedArticle;
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
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScriptGeneratorService {

    private final AppProperties appProperties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public VideoScriptJson generateEnglishScript(TopicGroup topic, String mergedStory) {
        log.info("Calling Ollama for script on topic: {}", topic.getTopic());

        // Clean and extract real summary content from articles
        String cleanedSummary = extractCleanSummary(topic.getArticles());
        String realTitle = extractRealTitle(topic.getArticles());
        String keywords = extractKeywords(topic.getArticles());

        log.info("Clean summary for prompt: {}", cleanedSummary.substring(0, Math.min(200, cleanedSummary.length())));

        String prompt = buildPrompt(realTitle, cleanedSummary, keywords);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> requestBody = Map.of(
                    "model", appProperties.getOllama().getModel(),
                    "prompt", prompt,
                    "stream", false,
                    "options", Map.of(
                            "temperature", 0.7,
                            "num_predict", 2000
                    )
            );

            ResponseEntity<Map> response = restTemplate.exchange(
                    appProperties.getOllama().getBaseUrl() + "/api/generate",
                    HttpMethod.POST,
                    new HttpEntity<>(requestBody, headers),
                    Map.class
            );

            String rawText = (String) response.getBody().get("response");
            log.debug("Raw Ollama response: {}", rawText);

            String cleanedJson = extractAndSanitizeJson(rawText);
            VideoScriptJson script = objectMapper.readValue(cleanedJson, VideoScriptJson.class);
            repairScript(script, topic.getTopic());
            log.info("Ollama script generated successfully for: {}", topic.getTopic());
            return script;

        } catch (Exception e) {
            log.error("Ollama call failed, using fallback: {}", e.getMessage());
            return buildFallbackScript(realTitle, cleanedSummary, keywords);
        }
    }

    /**
     * Repairs common Ollama output issues:
     * - Trims to exactly 3 scenes
     * - Fills blank/null fields with sensible defaults
     * - Ensures imageHints is never null or empty
     */
    private void repairScript(VideoScriptJson script, String topic) {
        if (script.getTitle() == null || script.getTitle().isBlank()) {
            script.setTitle("Breaking: " + topic);
        }
        if (script.getLanguage() == null || script.getLanguage().isBlank()) {
            script.setLanguage("en");
        }
        if (script.getNarrationStyle() == null || script.getNarrationStyle().isBlank()) {
            script.setNarrationStyle("professional news anchor");
        }

        List<VideoScriptJson.SceneJson> scenes = script.getScenes();
        if (scenes == null || scenes.isEmpty()) {
            script.setScenes(List.of(defaultScene("Hook", topic, 8),
                    defaultScene("Key facts", topic, 16),
                    defaultScene("Closing", topic, 7)));
            return;
        }

        // Trim to max 3 scenes — Ollama sometimes generates 4 or 5
        if (scenes.size() > 3) {
            script.setScenes(scenes.subList(0, 3));
            scenes = script.getScenes();
        }

        // Pad to exactly 3 scenes if fewer
        String[] defaultHeadings = {"Hook", "Key facts", "Closing"};
        int[] defaultDurations = {8, 16, 7};
        while (scenes.size() < 3) {
            int idx = scenes.size();
            scenes.add(defaultScene(defaultHeadings[idx], topic, defaultDurations[idx]));
        }

        // Fix each scene — fill any blank fields
        String[] fallbackVisuals = {
                "breaking news broadcast studio dramatic lighting",
                "documentary footage people affected by the story",
                "wide establishing shot calm reflective mood"
        };
        String[] fallbackHeadings = {"Hook", "Key facts", "Closing"};

        for (int i = 0; i < scenes.size(); i++) {
            VideoScriptJson.SceneJson scene = scenes.get(i);

            if (scene.getHeading() == null || scene.getHeading().isBlank()) {
                scene.setHeading(fallbackHeadings[i]);
            }
            if (scene.getNarration() == null || scene.getNarration().isBlank()) {
                scene.setNarration("This is a developing story about " + topic + ". More details are emerging.");
            }
            if (scene.getVisualPrompt() == null || scene.getVisualPrompt().isBlank()) {
                scene.setVisualPrompt(fallbackVisuals[i]);
            }
            if (scene.getDurationSeconds() <= 0) {
                scene.setDurationSeconds(defaultDurations[i]);
            }
            if (scene.getImageHints() == null || scene.getImageHints().isEmpty()) {
                scene.setImageHints(List.of(
                        topic.split(" ")[0].toLowerCase(), "news"
                ));
            }
        }
    }

    private VideoScriptJson.SceneJson defaultScene(String heading, String topic, int duration) {
        VideoScriptJson.SceneJson scene = new VideoScriptJson.SceneJson();
        scene.setHeading(heading);
        scene.setNarration("Developing story about " + topic + ". Updates are coming in.");
        scene.setVisualPrompt("news broadcast footage relevant to " + topic);
        scene.setDurationSeconds(duration);
        scene.setImageHints(List.of(topic.split(" ")[0].toLowerCase(), "news"));
        return scene;
    }

    // ─── SUMMARY EXTRACTION ──────────────────────────────────────────────────────

    /**
     * Extracts clean readable summary from articles.
     * Removes BBC nav menu garbage and keeps only real news sentences.
     */
    private String extractCleanSummary(List<FeedArticle> articles) {
        return articles.stream()
                .map(a -> {
                    // Prefer fullText over summary — it has the actual article content
                    String text = a.getFullText() != null && a.getFullText().length() > 200
                            ? a.getFullText()
                            : a.getSummary();
                    return cleanText(text);
                })
                .filter(t -> t != null && t.length() > 50)
                .findFirst()
                .orElse(articles.stream()
                        .map(a -> cleanText(a.getSummary()))
                        .filter(t -> t != null && !t.isBlank())
                        .collect(Collectors.joining(" ")));
    }

    /**
     * Removes navigation menus, HTML artifacts, and non-news content.
     * The BBC full text starts with huge nav menu before the actual article.
     */
    private String cleanText(String raw) {
        if (raw == null) return "";

        // BBC articles have nav menus — find where the real content starts
        // Real sentences start after the last occurrence of known nav patterns
        String[] navMarkers = {
                "Live Weather Newsletters",
                "Live News Live Sport",
                "Discover the World Live",
                "Skip to content",
                "Home News Sport Business"
        };

        String text = raw;
        for (String marker : navMarkers) {
            int idx = text.lastIndexOf(marker);
            if (idx != -1) {
                // Take content after the marker
                text = text.substring(idx + marker.length());
            }
        }

        // Remove common BBC boilerplate patterns
        text = text
                .replaceAll("BBC [A-Z][a-zA-Z]+", "")           // BBC News, BBC Sport etc
                .replaceAll("\\b(Share|Save|Subscribe|Watch|Listen|Read more)\\b", "")
                .replaceAll("\\d+ (hours?|minutes?|days?) ago", "")   // timestamps
                .replaceAll("[A-Z][a-z]+ [A-Z][a-z]+correspondent.*?(BBC|$)", "") // reporter bylines
                .replaceAll("\\s{2,}", " ")
                .trim();

        // Take only meaningful sentences — at least 20 chars, containing real words
        String[] sentences = text.split("(?<=[.!?])\\s+");
        StringBuilder result = new StringBuilder();
        for (String sentence : sentences) {
            sentence = sentence.trim();
            if (sentence.length() > 20
                    && sentence.split("\\s+").length > 4  // at least 5 words
                    && !sentence.matches(".*\\b(Home|Sport|Travel|Culture|Login)\\b.*")) {
                result.append(sentence).append(" ");
                if (result.length() > 1500) break;  // enough content for the script
            }
        }

        return result.toString().trim();
    }

    private String extractRealTitle(List<FeedArticle> articles) {
        return articles.stream()
                .map(FeedArticle::getTitle)
                .filter(t -> t != null && !t.isBlank())
                // Remove single quotes that BBC wraps around headlines
                .map(t -> t.replace("'", "").replace("\u2018", "").replace("\u2019", ""))
                .findFirst()
                .orElse("Breaking News");
    }

    private String extractKeywords(List<FeedArticle> articles) {
        // Pull meaningful words from titles — skip short/common words
        List<String> stopWords = List.of(
                "the", "a", "an", "and", "or", "but", "in", "on", "at", "to",
                "for", "of", "with", "is", "are", "was", "were", "be", "been",
                "have", "has", "had", "do", "does", "did", "will", "would",
                "can", "could", "may", "might", "shall", "should", "why", "how",
                "what", "who", "when", "where", "that", "this", "it", "its"
        );

        return articles.stream()
                .map(FeedArticle::getTitle)
                .filter(t -> t != null && !t.isBlank())
                .flatMap(t -> List.of(t.split("\\s+")).stream())
                .map(w -> w.replaceAll("[^a-zA-Z]", "").toLowerCase())
                .filter(w -> w.length() > 3)
                .filter(w -> !stopWords.contains(w))
                .distinct()
                .limit(5)
                .collect(Collectors.joining(", "));
    }

    // ─── PROMPT BUILDING ─────────────────────────────────────────────────────────

    private String buildPrompt(String realTitle, String cleanedSummary, String keywords) {
        // Take only first 1800 chars of cleaned summary — enough context without overloading
        String summary = cleanedSummary.length() > 1800
                ? cleanedSummary.substring(0, 1800) : cleanedSummary;

        // Extract 3 focused sentences for each scene from the summary
        String[] sentences = summary.split("(?<=[.!?])\\s+");

        String hookSentences = stripInnerQuotes(joinSentences(sentences, 0, 2));
        String factSentences = stripInnerQuotes(joinSentences(sentences, 2, 7));
        String closingSentences = stripInnerQuotes(joinSentences(sentences, Math.max(0, sentences.length - 3), sentences.length));

        return """
                You are a professional news video scriptwriter. Your task is to write narration \
                for a 30-40 second short video based ONLY on the article summary provided below.

                ARTICLE TITLE: %s

                ARTICLE SUMMARY (use this as the source of all narration):
                %s

                STORY KEYWORDS (use these for imageHints): %s

                STRICT RULES — follow all of these or output will be rejected:
                1. Write narration using ONLY facts from the article summary above
                2. Do NOT use apostrophes — write "it is" not "it's", "does not" not "don't"
                3. Do NOT use single quotes anywhere
                4. imageHints must be 1-2 simple single words from the story keywords above
                5. visualPrompt must describe a real scene matching this specific story
                6. Return ONLY raw JSON — no markdown, no code fences, no explanation text
                7. All JSON string values must use double quotes only
                8. Do NOT use double quotes inside narration text — rephrase instead
                9. Keep each narration as one continuous sentence without inner quotes

                SCENE CONTENT GUIDE:
                - Hook narration (8 seconds): use this from the article: "%s"
                - Key facts narration (16 seconds): use this from the article: "%s"
                - Closing narration (7 seconds): use this from the article: "%s"

                Return ONLY this JSON with all values replaced from the article — no placeholders:
                {
                  "title": "%s",
                  "language": "en",
                  "narrationStyle": "professional news anchor",
                  "scenes": [
                    {
                      "heading": "Hook",
                      "narration": "[write 8 seconds of narration from hook content above]",
                      "visualPrompt": "[describe a real scene from this story]",
                      "durationSeconds": 8,
                      "imageHints": ["[one keyword]", "[one keyword]"]
                    },
                    {
                      "heading": "Key facts",
                      "narration": "[write 16 seconds of narration from key facts above]",
                      "visualPrompt": "[describe a real scene from this story]",
                      "durationSeconds": 16,
                      "imageHints": ["[one keyword]", "[one keyword]"]
                    },
                    {
                      "heading": "Closing",
                      "narration": "[write 7 seconds of narration from closing content above]",
                      "visualPrompt": "[describe a real scene from this story]",
                      "durationSeconds": 7,
                      "imageHints": ["[one keyword]", "[one keyword]"]
                    }
                  ]
                }
                """.formatted(
                realTitle,
                summary,
                keywords,
                hookSentences,
                factSentences,
                closingSentences,
                realTitle
        );
    }

    private String stripInnerQuotes(String text) {
        if (text == null) return "";
        return text
                .replace("\"", "")        // remove double quotes
                .replace("\u201C", "")     // remove left double quote
                .replace("\u201D", "")     // remove right double quote
                .replace("  ", " ")
                .trim();
    }
    private String joinSentences(String[] sentences, int from, int to) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < Math.min(to, sentences.length); i++) {
            sb.append(sentences[i].trim()).append(" ");
        }
        return sb.toString().trim();
    }

    // ─── JSON EXTRACTION & SANITIZATION ──────────────────────────────────────────

    private String extractAndSanitizeJson(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new RuntimeException("Empty Ollama response");
        }

        // Strip markdown code fences if present
        raw = raw.replaceAll("(?s)```json\\s*", "")
                .replaceAll("(?s)```\\s*", "")
                .trim();

        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start == -1 || end == -1) {
            throw new RuntimeException("No JSON object found in Ollama response");
        }

        String json = raw.substring(start, end + 1);
        return sanitizeJson(json);
    }

    private String sanitizeJson(String json) {
        // Normalize smart quotes
        json = json
                .replace('\u2018', '\'')
                .replace('\u2019', '\'')
                .replace('\u201C', '"')
                .replace('\u201D', '"');

        StringBuilder result = new StringBuilder();
        boolean inString = false;
        boolean escaped = false;
        int depth = 0;

        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);

            if (escaped) {
                // Only allow valid JSON escape sequences
                if ("\"\\nrtbf/u".indexOf(c) >= 0) {
                    result.append(c);
                } else {
                    // Invalid escape — just append the char without backslash
                    result.append(c);
                }
                escaped = false;
                continue;
            }

            if (c == '\\') {
                escaped = true;
                result.append(c);
                continue;
            }

            if (c == '"') {
                if (!inString) {
                    inString = true;
                    result.append(c);
                } else {
                    // Check if this is a closing quote or an unescaped quote inside string
                    // Peek ahead — if next non-space char is : , } ] then it's a closing quote
                    int next = peekNextNonSpace(json, i + 1);
                    if (next == ':' || next == ',' || next == '}' || next == ']' || next == -1) {
                        inString = false;
                        result.append(c);
                    } else {
                        // Unescaped quote inside string value — escape it
                        result.append("\\\"");
                    }
                }
                continue;
            }

            if (inString) {
                if (c == '\'') {
                    result.append("\u2019");
                    continue;
                }
                if (c == '\n' || c == '\r') {
                    result.append(' ');
                    continue;
                }
                if (c == '\t') {
                    result.append(' ');
                    continue;
                }
                if (c < 0x20) {
                    continue;
                }
            } else {
                if (c == '{') depth++;
                if (c == '}') depth--;
            }

            result.append(c);
        }

        return result.toString();
    }

    private int peekNextNonSpace(String json, int from) {
        for (int i = from; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c != ' ' && c != '\t' && c != '\n' && c != '\r') {
                return c;
            }
        }
        return -1;
    }
    // ─── FALLBACK SCRIPT ─────────────────────────────────────────────────────────

    private VideoScriptJson buildFallbackScript(String realTitle, String cleanedSummary, String keywords) {
        String[] sentences = cleanedSummary.split("(?<=[.!?])\\s+");
        List<String> keywordList = List.of(keywords.split(",\\s*"));

        VideoScriptJson script = new VideoScriptJson();
        script.setTitle(realTitle.length() > 60 ? realTitle.substring(0, 60) : realTitle);
        script.setLanguage("en");
        script.setNarrationStyle("professional news anchor");

        VideoScriptJson.SceneJson hook = new VideoScriptJson.SceneJson();
        hook.setHeading("Hook");
        hook.setNarration(joinSentences(sentences, 0, 2));
        hook.setVisualPrompt("breaking news broadcast, dramatic atmosphere");
        hook.setDurationSeconds(8);
        hook.setImageHints(keywordList.size() >= 2
                ? List.of(keywordList.get(0).trim(), keywordList.get(1).trim())
                : List.of("news", "breaking"));

        VideoScriptJson.SceneJson facts = new VideoScriptJson.SceneJson();
        facts.setHeading("Key facts");
        facts.setNarration(joinSentences(sentences, 2, 7));
        facts.setVisualPrompt("documentary footage of the event, people affected");
        facts.setDurationSeconds(16);
        facts.setImageHints(keywordList.size() >= 4
                ? List.of(keywordList.get(2).trim(), keywordList.get(3).trim())
                : List.of("conflict", "world"));

        VideoScriptJson.SceneJson closing = new VideoScriptJson.SceneJson();
        closing.setHeading("Closing");
        closing.setNarration(joinSentences(sentences, Math.max(0, sentences.length - 3), sentences.length));
        closing.setVisualPrompt("wide establishing shot, reflective mood");
        closing.setDurationSeconds(7);
        closing.setImageHints(keywordList.size() >= 2
                ? List.of(keywordList.get(0).trim(), "world")
                : List.of("peace", "world"));

        script.setScenes(List.of(hook, facts, closing));
        return script;
    }
}
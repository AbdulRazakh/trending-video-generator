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

import java.util.ArrayList;
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

    // Minimum summary length to bother calling Ollama
    private static final int MIN_SUMMARY_FOR_OLLAMA = 300;

    public VideoScriptJson generateEnglishScript(TopicGroup topic, String mergedStory) {
        log.info("Calling Ollama for script on topic: {}", topic.getTopic());

        String cleanedSummary = extractCleanSummary(topic.getArticles());
        String realTitle = extractRealTitle(topic.getArticles());
        String keywords = extractKeywords(topic.getArticles());

        log.info("Clean summary length: {}", cleanedSummary.length());

        // If summary is too short, skip Ollama — just build directly from real text
        if (cleanedSummary.length() < MIN_SUMMARY_FOR_OLLAMA) {
            log.info("Summary too short for Ollama — building script directly from article text");
            return buildDirectScript(realTitle, cleanedSummary, keywords, topic.getTopic());
        }

        try {
            String prompt = buildPrompt(realTitle, cleanedSummary, keywords);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> requestBody = Map.of(
                    "model", appProperties.getOllama().getModel(),
                    "prompt", prompt,
                    "stream", false,
                    "options", Map.of(
                            "temperature", 0.5,       // lower = more consistent output
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
            String cleanedJson = extractAndSanitizeJson(rawText);
            VideoScriptJson script = objectMapper.readValue(cleanedJson, VideoScriptJson.class);
            repairScript(script, realTitle, cleanedSummary, keywords, topic.getTopic());
            log.info("Ollama script generated successfully for: {}", topic.getTopic());
            return script;

        } catch (Exception e) {
            log.error("Ollama failed, building direct script: {}", e.getMessage());
            return buildDirectScript(realTitle, cleanedSummary, keywords, topic.getTopic());
        }
    }

    // ─── DIRECT SCRIPT — no Ollama, pure article text ────────────────────────────

    /**
     * Builds a script directly from article sentences — no LLM needed.
     * Guaranteed to always produce valid narration from real content.
     */
    private VideoScriptJson buildDirectScript(String realTitle, String cleanedSummary,
                                              String keywords, String topic) {
        List<String> sentences = splitSentences(cleanedSummary);

        // Pad sentences if too few
        while (sentences.size() < 6) {
            sentences.add("This story about " + topic + " continues to develop.");
        }

        String hookNarration = joinRange(sentences, 0, 2);
        String factsNarration = joinRange(sentences, 2, 6);
        String closingNarration = joinRange(sentences, Math.max(0, sentences.size() - 2), sentences.size());

        List<String> kwList = List.of(keywords.split(",\\s*"));

        VideoScriptJson script = new VideoScriptJson();
        script.setTitle(truncate(realTitle, 60));
        script.setLanguage("en");
        script.setNarrationStyle("professional news anchor");

        VideoScriptJson.SceneJson hook = new VideoScriptJson.SceneJson();
        hook.setHeading("Hook");
        hook.setNarration(ensureNotBlank(hookNarration, "Breaking news on " + topic));
        hook.setVisualPrompt("breaking news broadcast, dramatic studio lighting, camera zoom in");
        hook.setDurationSeconds(8);
        hook.setImageHints(safeKeywords(kwList, 0, 1, topic));

        VideoScriptJson.SceneJson facts = new VideoScriptJson.SceneJson();
        facts.setHeading("Key facts");
        facts.setNarration(ensureNotBlank(factsNarration, "Details are emerging about " + topic));
        facts.setVisualPrompt("documentary footage, people affected, key locations related to story");
        facts.setDurationSeconds(16);
        facts.setImageHints(safeKeywords(kwList, 2, 3, topic));

        VideoScriptJson.SceneJson closing = new VideoScriptJson.SceneJson();
        closing.setHeading("Closing");
        closing.setNarration(ensureNotBlank(closingNarration, "Developments in " + topic + " are expected to continue."));
        closing.setVisualPrompt("wide establishing shot, calm reflective mood, related to story");
        closing.setDurationSeconds(7);
        closing.setImageHints(safeKeywords(kwList, 0, 2, topic));

        script.setScenes(List.of(hook, facts, closing));
        return script;
    }

    // ─── REPAIR — fixes Ollama output issues ─────────────────────────────────────

    private void repairScript(VideoScriptJson script, String realTitle, String cleanedSummary,
                              String keywords, String topic) {
        if (script.getTitle() == null || script.getTitle().isBlank()) {
            script.setTitle(truncate(realTitle, 60));
        }
        if (script.getLanguage() == null || script.getLanguage().isBlank()) {
            script.setLanguage("en");
        }
        if (script.getNarrationStyle() == null || script.getNarrationStyle().isBlank()) {
            script.setNarrationStyle("professional news anchor");
        }

        List<VideoScriptJson.SceneJson> scenes = script.getScenes();
        if (scenes == null || scenes.isEmpty()) {
            script.setScenes(buildDirectScript(realTitle, cleanedSummary, keywords, topic).getScenes());
            return;
        }

        // Trim to max 3
        if (scenes.size() > 3) {
            script.setScenes(new ArrayList<>(scenes.subList(0, 3)));
            scenes = script.getScenes();
        }

        List<String> sentences = splitSentences(cleanedSummary);
        while (sentences.size() < 6) {
            sentences.add("This story continues to develop.");
        }

        String[] fallbackNarrations = {
                joinRange(sentences, 0, 2),
                joinRange(sentences, 2, 6),
                joinRange(sentences, Math.max(0, sentences.size() - 2), sentences.size())
        };
        String[] fallbackVisuals = {
                "breaking news broadcast studio dramatic lighting",
                "documentary footage people affected by the story",
                "wide establishing shot calm reflective mood"
        };
        String[] fallbackHeadings = {"Hook", "Key facts", "Closing"};
        int[] fallbackDurations = {8, 16, 7};
        List<String> kwList = List.of(keywords.split(",\\s*"));

        // Pad to 3 scenes
        while (scenes.size() < 3) {
            int i = scenes.size();
            VideoScriptJson.SceneJson s = new VideoScriptJson.SceneJson();
            s.setHeading(fallbackHeadings[i]);
            s.setNarration(fallbackNarrations[i]);
            s.setVisualPrompt(fallbackVisuals[i]);
            s.setDurationSeconds(fallbackDurations[i]);
            s.setImageHints(safeKeywords(kwList, i, i + 1, topic));
            scenes.add(s);
        }

        // Fix each scene
        for (int i = 0; i < scenes.size(); i++) {
            VideoScriptJson.SceneJson scene = scenes.get(i);
            if (scene.getHeading() == null || scene.getHeading().isBlank())
                scene.setHeading(fallbackHeadings[i]);
            if (scene.getNarration() == null || scene.getNarration().isBlank())
                scene.setNarration(ensureNotBlank(fallbackNarrations[i], "Developing story about " + topic));
            if (scene.getVisualPrompt() == null || scene.getVisualPrompt().isBlank())
                scene.setVisualPrompt(fallbackVisuals[i]);
            if (scene.getDurationSeconds() <= 0)
                scene.setDurationSeconds(fallbackDurations[i]);
            if (scene.getImageHints() == null || scene.getImageHints().isEmpty())
                scene.setImageHints(safeKeywords(kwList, i, i + 1, topic));
        }
    }

    // ─── PROMPT ──────────────────────────────────────────────────────────────────

    private String buildPrompt(String realTitle, String cleanedSummary, String keywords) {
        String summary = cleanedSummary.length() > 1500
                ? cleanedSummary.substring(0, 1500) : cleanedSummary;

        List<String> sentences = splitSentences(summary);
        String hook = stripInnerQuotes(joinRange(sentences, 0, 2));
        String facts = stripInnerQuotes(joinRange(sentences, 2, 6));
        String closing = stripInnerQuotes(joinRange(sentences,
                Math.max(0, sentences.size() - 2), sentences.size()));

        return "You are a news video scriptwriter. Write a 30 second video script as JSON.\n\n"
                + "TITLE: " + stripInnerQuotes(realTitle) + "\n\n"
                + "ARTICLE SUMMARY:\n" + stripInnerQuotes(summary) + "\n\n"
                + "KEYWORDS FOR IMAGES: " + keywords + "\n\n"
                + "RULES:\n"
                + "- Return ONLY raw JSON. No markdown. No explanation.\n"
                + "- Never use apostrophes. Write: it is, do not, cannot\n"
                + "- Never use double quotes inside string values\n"
                + "- imageHints must be single simple words from the keywords list\n"
                + "- Write exactly 3 scenes, no more\n\n"
                + "USE THESE SENTENCES FOR NARRATION:\n"
                + "Hook: " + hook + "\n"
                + "Facts: " + facts + "\n"
                + "Closing: " + closing + "\n\n"
                + "JSON:\n"
                + "{\n"
                + "  \"title\": \"" + truncate(stripInnerQuotes(realTitle), 50) + "\",\n"
                + "  \"language\": \"en\",\n"
                + "  \"narrationStyle\": \"professional news anchor\",\n"
                + "  \"scenes\": [\n"
                + "    {\"heading\": \"Hook\", \"narration\": \"REPLACE WITH HOOK NARRATION\","
                + " \"visualPrompt\": \"REPLACE WITH REAL VISUAL\","
                + " \"durationSeconds\": 8, \"imageHints\": [\"keyword1\", \"keyword2\"]},\n"
                + "    {\"heading\": \"Key facts\", \"narration\": \"REPLACE WITH FACTS NARRATION\","
                + " \"visualPrompt\": \"REPLACE WITH REAL VISUAL\","
                + " \"durationSeconds\": 16, \"imageHints\": [\"keyword1\", \"keyword2\"]},\n"
                + "    {\"heading\": \"Closing\", \"narration\": \"REPLACE WITH CLOSING NARRATION\","
                + " \"visualPrompt\": \"REPLACE WITH REAL VISUAL\","
                + " \"durationSeconds\": 7, \"imageHints\": [\"keyword1\", \"keyword2\"]}\n"
                + "  ]\n"
                + "}";
    }

    // ─── TEXT CLEANING ────────────────────────────────────────────────────────────

    private String extractCleanSummary(List<FeedArticle> articles) {
        return articles.stream()
                .map(a -> {
                    String text = a.getFullText() != null && a.getFullText().length() > 200
                            ? a.getFullText() : a.getSummary();
                    return cleanText(text);
                })
                .filter(t -> t != null && t.length() > 30)
                .findFirst()
                .orElse(articles.stream()
                        .map(a -> cleanText(a.getSummary()))
                        .filter(t -> t != null && !t.isBlank())
                        .collect(Collectors.joining(" ")));
    }

    private String cleanText(String raw) {
        if (raw == null) return "";

        String[] navMarkers = {
                "Live Weather Newsletters", "Live News Live Sport",
                "Discover the World Live", "Skip to content",
                "Home News Sport Business"
        };
        String text = raw;
        for (String marker : navMarkers) {
            int idx = text.lastIndexOf(marker);
            if (idx != -1) text = text.substring(idx + marker.length());
        }

        text = text
                .replaceAll("BBC [A-Z][a-zA-Z]+", "")
                .replaceAll("\\b(Share|Save|Subscribe|Watch|Listen|Read more)\\b", "")
                .replaceAll("\\d+ (hours?|minutes?|days?) ago", "")
                .replaceAll("\\s{2,}", " ")
                .trim();

        String[] sentences = text.split("(?<=[.!?])\\s+");
        StringBuilder result = new StringBuilder();
        for (String sentence : sentences) {
            sentence = sentence.trim();
            if (sentence.length() > 20 && sentence.split("\\s+").length > 4) {
                result.append(sentence).append(" ");
                if (result.length() > 1500) break;
            }
        }
        return result.toString().trim();
    }

    private String extractRealTitle(List<FeedArticle> articles) {
        return articles.stream()
                .map(FeedArticle::getTitle)
                .filter(t -> t != null && !t.isBlank())
                .map(t -> t.replace("'", "").replace("\u2018", "").replace("\u2019", ""))
                .findFirst()
                .orElse("Breaking News");
    }

    private String extractKeywords(List<FeedArticle> articles) {
        List<String> stopWords = List.of(
                "the","a","an","and","or","but","in","on","at","to","for","of",
                "with","is","are","was","were","be","been","have","has","had",
                "do","does","did","will","would","can","could","may","might",
                "why","how","what","who","when","where","that","this","it","its"
        );
        return articles.stream()
                .map(FeedArticle::getTitle)
                .filter(t -> t != null && !t.isBlank())
                .flatMap(t -> List.of(t.split("\\s+")).stream())
                .map(w -> w.replaceAll("[^a-zA-Z]", "").toLowerCase())
                .filter(w -> w.length() > 3)
                .filter(w -> !stopWords.contains(w))
                .distinct()
                .limit(6)
                .collect(Collectors.joining(", "));
    }

    // ─── JSON EXTRACTION ─────────────────────────────────────────────────────────

    private String extractAndSanitizeJson(String raw) {
        if (raw == null || raw.isBlank())
            throw new RuntimeException("Empty Ollama response");

        raw = raw.replaceAll("(?s)```json\\s*", "")
                .replaceAll("(?s)```\\s*", "").trim();

        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start == -1 || end == -1)
            throw new RuntimeException("No JSON found in Ollama response");

        return sanitizeJson(raw.substring(start, end + 1));
    }

    private String sanitizeJson(String json) {
        json = json
                .replace('\u2018', '\'').replace('\u2019', '\'')
                .replace('\u201C', '"').replace('\u201D', '"');

        StringBuilder result = new StringBuilder();
        boolean inString = false;
        boolean escaped = false;

        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaped) { result.append(c); escaped = false; continue; }
            if (c == '\\') { escaped = true; result.append(c); continue; }
            if (c == '"') {
                if (!inString) {
                    inString = true;
                    result.append(c);
                } else {
                    int next = peekNextNonSpace(json, i + 1);
                    if (next == ':' || next == ',' || next == '}' || next == ']' || next == -1) {
                        inString = false;
                        result.append(c);
                    } else {
                        result.append("\\\"");
                    }
                }
                continue;
            }
            if (inString) {
                if (c == '\'') { result.append("\u2019"); continue; }
                if (c == '\n' || c == '\r' || c == '\t') { result.append(' '); continue; }
                if (c < 0x20) continue;
            }
            result.append(c);
        }
        return result.toString();
    }

    private int peekNextNonSpace(String json, int from) {
        for (int i = from; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c != ' ' && c != '\t' && c != '\n' && c != '\r') return c;
        }
        return -1;
    }

    // ─── HELPERS ─────────────────────────────────────────────────────────────────

    private List<String> splitSentences(String text) {
        if (text == null || text.isBlank()) return new ArrayList<>();
        List<String> result = new ArrayList<>();
        for (String s : text.split("(?<=[.!?])\\s+")) {
            s = s.trim();
            if (s.length() > 15) result.add(s);
        }
        return result;
    }

    private String joinRange(List<String> sentences, int from, int to) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < Math.min(to, sentences.size()); i++) {
            sb.append(sentences.get(i)).append(" ");
        }
        return sb.toString().trim();
    }

    private String ensureNotBlank(String text, String fallback) {
        return (text == null || text.isBlank()) ? fallback : text;
    }

    private String truncate(String text, int max) {
        if (text == null) return "";
        return text.length() <= max ? text : text.substring(0, max);
    }

    private String stripInnerQuotes(String text) {
        if (text == null) return "";
        return text.replace("\"", "").replace("\u201C", "").replace("\u201D", "")
                .replaceAll("\\s{2,}", " ").trim();
    }

    private List<String> safeKeywords(List<String> kwList, int from, int to, String topic) {
        List<String> result = new ArrayList<>();
        for (int i = from; i < Math.min(to + 1, kwList.size()); i++) {
            String kw = kwList.get(i).trim();
            if (!kw.isBlank()) result.add(kw);
        }
        if (result.isEmpty()) result.add(topic.split(" ")[0].toLowerCase());
        if (result.size() < 2) result.add("news");
        return result;
    }
}
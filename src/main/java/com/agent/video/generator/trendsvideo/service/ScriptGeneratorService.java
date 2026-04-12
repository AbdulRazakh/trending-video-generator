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

import java.util.*;
import java.util.stream.Collectors;

/**
 * Generates video scripts using Groq API (primary) with Ollama as fallback.
 *
 * Groq: free at console.groq.com — llama3-70b produces clean JSON, no apostrophe issues.
 * Ollama: kept as local fallback when Groq rate limit hit or unavailable.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScriptGeneratorService {

    private final AppProperties appProperties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    private static final int MIN_CONTENT_FOR_LLM = 200;

    public VideoScriptJson generateEnglishScript(TopicGroup topic, String mergedStory) {
        log.info("Generating script for topic: {}", topic.getTopic());

        String cleanContent = extractCleanContent(topic.getArticles());
        String realTitle = extractRealTitle(topic.getArticles());
        String keywords = extractKeywords(topic.getArticles());
        String sourceName = extractSourceName(topic.getArticles());

        log.info("Content length: {} chars, source: {}", cleanContent.length(), sourceName);

        // Skip LLM entirely for very short content — build direct from article text
        if (cleanContent.length() < MIN_CONTENT_FOR_LLM) {
            log.info("Content too short for LLM — building direct script");
            return buildDirectScript(realTitle, cleanContent, keywords, topic.getTopic());
        }

        // Try Groq first (fast, reliable, free)
        AppProperties.Groq groqCfg = appProperties.getGroq();
        if (groqCfg != null && groqCfg.getApiKey() != null && !groqCfg.getApiKey().startsWith("YOUR_")) {
            try {
                VideoScriptJson script = callGroq(realTitle, cleanContent, keywords, topic.getTopic());
                repairScript(script, realTitle, cleanContent, keywords, topic.getTopic());
                log.info("Groq script generated successfully for: {}", topic.getTopic());
                return script;
            } catch (Exception e) {
                log.warn("Groq failed ({}), trying Ollama fallback", e.getMessage());
            }
        }

        // Fallback to Ollama
        AppProperties.Ollama ollamaCfg = appProperties.getOllama();
        if (ollamaCfg != null) {
            try {
                VideoScriptJson script = callOllama(realTitle, cleanContent, keywords, topic.getTopic());
                repairScript(script, realTitle, cleanContent, keywords, topic.getTopic());
                log.info("Ollama script generated for: {}", topic.getTopic());
                return script;
            } catch (Exception e) {
                log.warn("Ollama failed ({}), using direct script", e.getMessage());
            }
        }

        // Final fallback — always works
        log.info("Using direct script for: {}", topic.getTopic());
        return buildDirectScript(realTitle, cleanContent, keywords, topic.getTopic());
    }

    // ─── GROQ API ────────────────────────────────────────────────────────────────

    private VideoScriptJson callGroq(String title, String content, String keywords, String topic) throws Exception {
        AppProperties.Groq cfg = appProperties.getGroq();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(cfg.getApiKey());

        String systemPrompt = "You are a professional news video scriptwriter. "
            + "You always return valid JSON only. "
            + "Never use apostrophes in contractions — write 'it is' not 'it's'. "
            + "Never use double quotes inside string values. "
            + "Return exactly 3 scenes, no more, no less.";

        String userPrompt = buildPrompt(title, content, keywords);

        Map<String, Object> requestBody = Map.of(
            "model", cfg.getModel(),
            "messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
            ),
            "temperature", 0.4,
            "max_tokens", 1500,
            "response_format", Map.of("type", "json_object")  // forces valid JSON output
        );

        ResponseEntity<Map> response = restTemplate.exchange(
            cfg.getBaseUrl() + "/chat/completions",
            HttpMethod.POST,
            new HttpEntity<>(requestBody, headers),
            Map.class
        );

        Map body = response.getBody();
        List<Map<String, Object>> choices = (List<Map<String, Object>>) body.get("choices");
        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        String rawJson = (String) message.get("content");

        log.debug("Groq raw response: {}", rawJson);
        return objectMapper.readValue(sanitizeJson(rawJson), VideoScriptJson.class);
    }

    // ─── OLLAMA API ───────────────────────────────────────────────────────────────

    private VideoScriptJson callOllama(String title, String content, String keywords, String topic) throws Exception {
        AppProperties.Ollama cfg = appProperties.getOllama();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> requestBody = Map.of(
            "model", cfg.getModel(),
            "prompt", buildPrompt(title, content, keywords),
            "stream", false,
            "options", Map.of("temperature", 0.4, "num_predict", 2000)
        );

        ResponseEntity<Map> response = restTemplate.exchange(
            cfg.getBaseUrl() + "/api/generate",
            HttpMethod.POST,
            new HttpEntity<>(requestBody, headers),
            Map.class
        );

        String rawText = (String) response.getBody().get("response");
        String cleanedJson = extractAndSanitizeJson(rawText);
        return objectMapper.readValue(cleanedJson, VideoScriptJson.class);
    }

    // ─── PROMPT ──────────────────────────────────────────────────────────────────

    private String buildPrompt(String title, String content, String keywords) {
        String safeContent = stripQuotes(content.length() > 1500 ? content.substring(0, 1500) : content);
        List<String> sentences = splitSentences(safeContent);

        String hook    = joinRange(sentences, 0, 2);
        String facts   = joinRange(sentences, 2, 6);
        String closing = joinRange(sentences, Math.max(0, sentences.size() - 2), sentences.size());

        return "Write a 30-second news video script as JSON for this article.\n\n"
            + "HEADLINE: " + stripQuotes(title) + "\n\n"
            + "ARTICLE CONTENT:\n" + safeContent + "\n\n"
            + "IMAGE KEYWORDS: " + keywords + "\n\n"
            + "RULES:\n"
            + "- Return ONLY the JSON object, nothing else\n"
            + "- Use only words from the article for narration — do not invent facts\n"
            + "- No apostrophes: write 'it is' not 'it's', 'does not' not 'don't'\n"
            + "- No double quotes inside string values\n"
            + "- imageHints: pick 1-2 single words from image keywords above\n"
            + "- Exactly 3 scenes\n\n"
            + "BASE NARRATION ON THESE SENTENCES:\n"
            + "Hook: " + hook + "\n"
            + "Facts: " + facts + "\n"
            + "Closing: " + closing + "\n\n"
            + "RETURN THIS JSON STRUCTURE:\n"
            + "{\n"
            + "  \"title\": \"" + truncate(stripQuotes(title), 55) + "\",\n"
            + "  \"language\": \"en\",\n"
            + "  \"narrationStyle\": \"professional news anchor\",\n"
            + "  \"scenes\": [\n"
            + "    {\"heading\":\"Hook\",\"narration\":\"[8 seconds from hook sentences]\","
            + "\"visualPrompt\":\"[real scene for this story]\","
            + "\"durationSeconds\":8,\"imageHints\":[\"[keyword]\",\"[keyword]\"]},\n"
            + "    {\"heading\":\"Key facts\",\"narration\":\"[16 seconds from facts sentences]\","
            + "\"visualPrompt\":\"[real scene for this story]\","
            + "\"durationSeconds\":16,\"imageHints\":[\"[keyword]\",\"[keyword]\"]},\n"
            + "    {\"heading\":\"Closing\",\"narration\":\"[7 seconds from closing sentences]\","
            + "\"visualPrompt\":\"[real scene for this story]\","
            + "\"durationSeconds\":7,\"imageHints\":[\"[keyword]\",\"[keyword]\"]}\n"
            + "  ]\n"
            + "}";
    }

    // ─── DIRECT SCRIPT — no LLM ──────────────────────────────────────────────────

    /**
     * Builds script directly from article sentences.
     * Guaranteed to always produce valid, non-blank content.
     */
    private VideoScriptJson buildDirectScript(String title, String content,
                                               String keywords, String topic) {
        List<String> sentences = splitSentences(content);
        while (sentences.size() < 6) {
            sentences.add("This story about " + topic + " continues to develop.");
        }

        List<String> kwList = parseKeywords(keywords);

        VideoScriptJson script = new VideoScriptJson();
        script.setTitle(truncate(title, 60));
        script.setLanguage("en");
        script.setNarrationStyle("professional news anchor");

        VideoScriptJson.SceneJson hook = new VideoScriptJson.SceneJson();
        hook.setHeading("Hook");
        hook.setNarration(ensureNotBlank(joinRange(sentences, 0, 2), "Breaking story: " + topic));
        hook.setVisualPrompt("breaking news broadcast, dramatic studio lighting");
        hook.setDurationSeconds(8);
        hook.setImageHints(safeHints(kwList, 0, 2, topic));

        VideoScriptJson.SceneJson facts = new VideoScriptJson.SceneJson();
        facts.setHeading("Key facts");
        facts.setNarration(ensureNotBlank(joinRange(sentences, 2, 6), "Details emerging about " + topic));
        facts.setVisualPrompt("documentary footage, relevant locations, people affected");
        facts.setDurationSeconds(16);
        facts.setImageHints(safeHints(kwList, 2, 4, topic));

        VideoScriptJson.SceneJson closing = new VideoScriptJson.SceneJson();
        closing.setHeading("Closing");
        closing.setNarration(ensureNotBlank(
            joinRange(sentences, Math.max(0, sentences.size() - 2), sentences.size()),
            "This story continues to develop."
        ));
        closing.setVisualPrompt("wide establishing shot, calm reflective mood");
        closing.setDurationSeconds(7);
        closing.setImageHints(safeHints(kwList, 0, 2, topic));

        script.setScenes(List.of(hook, facts, closing));
        return script;
    }

    // ─── CONTENT EXTRACTION ──────────────────────────────────────────────────────

    private String extractCleanContent(List<FeedArticle> articles) {
        return articles.stream()
            .map(a -> {
                // NewsAPI provides clean content directly — no nav menu scraping needed
                String text = a.getFullText() != null && a.getFullText().length() > 100
                        ? a.getFullText() : a.getSummary();
                return text != null ? text.trim() : "";
            })
            .filter(t -> t.length() > 50)
            .collect(Collectors.joining(" "))
            .replaceAll("\\s{2,}", " ")
            .trim();
    }

    private String extractRealTitle(List<FeedArticle> articles) {
        return articles.stream()
            .map(FeedArticle::getTitle)
            .filter(t -> t != null && !t.isBlank())
            .map(t -> t.replace("'", "").replace("\u2018", "").replace("\u2019", ""))
            .findFirst()
            .orElse("Breaking News");
    }

    private String extractSourceName(List<FeedArticle> articles) {
        return articles.stream()
            .map(FeedArticle::getSource)
            .filter(s -> s != null && !s.isBlank())
            .findFirst()
            .orElse("NewsAPI");
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
            .flatMap(t -> Arrays.stream(t.split("\\s+")))
            .map(w -> w.replaceAll("[^a-zA-Z]", "").toLowerCase())
            .filter(w -> w.length() > 3 && !stopWords.contains(w))
            .distinct()
            .limit(8)
            .collect(Collectors.joining(", "));
    }

    // ─── REPAIR ──────────────────────────────────────────────────────────────────

    private void repairScript(VideoScriptJson script, String title, String content,
                               String keywords, String topic) {
        if (script.getTitle() == null || script.getTitle().isBlank())
            script.setTitle(truncate(title, 60));
        if (script.getLanguage() == null || script.getLanguage().isBlank())
            script.setLanguage("en");
        if (script.getNarrationStyle() == null || script.getNarrationStyle().isBlank())
            script.setNarrationStyle("professional news anchor");

        List<VideoScriptJson.SceneJson> scenes = script.getScenes();
        if (scenes == null || scenes.isEmpty()) {
            script.setScenes(buildDirectScript(title, content, keywords, topic).getScenes());
            return;
        }

        if (scenes.size() > 3) script.setScenes(new ArrayList<>(scenes.subList(0, 3)));

        List<String> sentences = splitSentences(content);
        while (sentences.size() < 6) sentences.add("This story continues to develop.");

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
        String[] headings = {"Hook", "Key facts", "Closing"};
        int[] durations = {8, 16, 7};
        List<String> kwList = parseKeywords(keywords);

        while (script.getScenes().size() < 3) {
            int i = script.getScenes().size();
            VideoScriptJson.SceneJson s = new VideoScriptJson.SceneJson();
            s.setHeading(headings[i]);
            s.setNarration(fallbackNarrations[i]);
            s.setVisualPrompt(fallbackVisuals[i]);
            s.setDurationSeconds(durations[i]);
            s.setImageHints(safeHints(kwList, i, i + 2, topic));
            script.getScenes().add(s);
        }

        for (int i = 0; i < script.getScenes().size(); i++) {
            VideoScriptJson.SceneJson s = script.getScenes().get(i);
            if (s.getHeading() == null || s.getHeading().isBlank()) s.setHeading(headings[i]);
            if (s.getNarration() == null || s.getNarration().isBlank())
                s.setNarration(ensureNotBlank(fallbackNarrations[i], "Story: " + topic));
            if (s.getVisualPrompt() == null || s.getVisualPrompt().isBlank())
                s.setVisualPrompt(fallbackVisuals[i]);
            if (s.getDurationSeconds() <= 0) s.setDurationSeconds(durations[i]);
            if (s.getImageHints() == null || s.getImageHints().isEmpty())
                s.setImageHints(safeHints(kwList, i, i + 2, topic));
        }
    }

    // ─── JSON SANITIZATION ────────────────────────────────────────────────────────

    private String extractAndSanitizeJson(String raw) {
        if (raw == null || raw.isBlank()) throw new RuntimeException("Empty response");
        raw = raw.replaceAll("(?s)```json\\s*", "").replaceAll("(?s)```\\s*", "").trim();
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start == -1 || end == -1) throw new RuntimeException("No JSON in response");
        return sanitizeJson(raw.substring(start, end + 1));
    }

    private String sanitizeJson(String json) {
        json = json.replace('\u2018','\'').replace('\u2019','\'')
                   .replace('\u201C','"').replace('\u201D','"');

        StringBuilder result = new StringBuilder();
        boolean inString = false, escaped = false;

        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaped) { result.append(c); escaped = false; continue; }
            if (c == '\\') { escaped = true; result.append(c); continue; }
            if (c == '"') {
                if (!inString) { inString = true; result.append(c); }
                else {
                    int next = peekNext(json, i + 1);
                    if (next==':' || next==',' || next=='}' || next==']' || next==-1) {
                        inString = false; result.append(c);
                    } else { result.append("\\\""); }
                }
                continue;
            }
            if (inString) {
                if (c=='\'') { result.append("\u2019"); continue; }
                if (c=='\n'||c=='\r'||c=='\t') { result.append(' '); continue; }
                if (c < 0x20) continue;
            }
            result.append(c);
        }
        return result.toString();
    }

    private int peekNext(String s, int from) {
        for (int i = from; i < s.length(); i++) {
            char c = s.charAt(i);
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
        for (int i = from; i < Math.min(to, sentences.size()); i++)
            sb.append(sentences.get(i)).append(" ");
        return sb.toString().trim();
    }

    private String ensureNotBlank(String text, String fallback) {
        return (text == null || text.isBlank()) ? fallback : text;
    }

    private String truncate(String text, int max) {
        if (text == null) return "";
        return text.length() <= max ? text : text.substring(0, max);
    }

    private String stripQuotes(String text) {
        if (text == null) return "";
        return text.replace("\"","").replace("\u201C","").replace("\u201D","")
                   .replaceAll("\\s{2,}"," ").trim();
    }

    private List<String> parseKeywords(String keywords) {
        if (keywords == null || keywords.isBlank()) return new ArrayList<>();
        return Arrays.asList(keywords.split(",\\s*"));
    }

    private List<String> safeHints(List<String> kwList, int from, int to, String topic) {
        List<String> result = new ArrayList<>();
        for (int i = from; i < Math.min(to, kwList.size()); i++) {
            String kw = kwList.get(i).trim();
            if (!kw.isBlank() && kw.length() > 2) result.add(kw);
        }
        if (result.isEmpty()) result.add(topic.split(" ")[0].toLowerCase());
        if (result.size() < 2) result.add("news");
        return result.subList(0, Math.min(2, result.size()));
    }
}

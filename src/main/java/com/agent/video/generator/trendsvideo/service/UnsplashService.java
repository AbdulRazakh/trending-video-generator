package com.agent.video.generator.trendsvideo.service;

import com.agent.video.generator.trendsvideo.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UnsplashService {

    private final AppProperties appProperties;
    private final RestTemplate restTemplate;

    public String downloadImage(List<String> imageHints, String workspaceDir) throws IOException {
        Files.createDirectories(Path.of(workspaceDir));

        String query = String.join(" ", imageHints);
        String accessKey = appProperties.getUnsplash().getAccessKey();

        try {
            String searchUrl = "https://api.unsplash.com/search/photos"
                    + "?query=" + query.replace(" ", "+")
                    + "&orientation=portrait"
                    + "&per_page=3"
                    + "&client_id=" + accessKey;

            Map body = restTemplate.getForObject(searchUrl, Map.class);
            List<Map<String, Object>> results = (List<Map<String, Object>>) body.get("results");

            if (results == null || results.isEmpty()) {
                log.warn("No Unsplash results for '{}', using fallback", query);
                return generateFallbackImage(workspaceDir, query);
            }

            // Pick a random one from top 3 for variety
            int pick = (int) (Math.random() * Math.min(3, results.size()));
            Map<String, Object> urls = (Map<String, Object>) results.get(pick).get("urls");
            String imageUrl = (String) urls.get("regular"); // 1080px wide

            byte[] imageBytes = restTemplate.getForObject(imageUrl, byte[].class);
            String imagePath = Path.of(workspaceDir, "bg_" + UUID.randomUUID() + ".jpg").toString();
            Files.write(Path.of(imagePath), imageBytes);
            log.info("Unsplash image downloaded for '{}': {}", query, imagePath);
            return imagePath;

        } catch (Exception e) {
            log.error("Unsplash failed for '{}': {}", query, e.getMessage());
            return generateFallbackImage(workspaceDir, query);
        }
    }

    private String generateFallbackImage(String workspaceDir, String query) throws IOException {
        String imagePath = Path.of(workspaceDir, "fallback_" + UUID.randomUUID() + ".jpg").toString();
        try {
            // Use full ffmpeg path from config instead of just "ffmpeg"
            String ffmpeg = appProperties.getVideo().getFfmpegPath();
            ProcessBuilder pb = new ProcessBuilder(
                    ffmpeg,
                    "-f", "lavfi",
                    "-i", "color=c=0x1a1a2e:s=1080x1920",
                    "-frames:v", "1",
                    "-y", imagePath
            );
            pb.redirectErrorStream(true);
            pb.start().waitFor();
            log.info("Fallback image created: {}", imagePath);
        } catch (Exception ex) {
            log.error("Fallback image generation failed", ex);
        }
        return imagePath;
    }
}
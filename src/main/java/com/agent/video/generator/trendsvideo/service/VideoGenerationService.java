package com.agent.video.generator.trendsvideo.service;

import com.agent.video.generator.trendsvideo.config.AppProperties;
import com.agent.video.generator.trendsvideo.dto.VideoScriptJson;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VideoGenerationService {

    private final AppProperties appProperties;

    public String generateVideo(VideoScriptJson script) {
        try {
            Files.createDirectories(Path.of(appProperties.getVideo().getOutputDir()));

            String ffmpegPath = appProperties.getVideo().getFfmpegPath();
            File ffmpegFile = new File(ffmpegPath);

            if (!"ffmpeg".equalsIgnoreCase(ffmpegPath) && !ffmpegFile.exists()) {
                throw new RuntimeException("FFmpeg not found at configured path: " + ffmpegPath);
            }

            String fileName = "video_" + UUID.randomUUID() + ".mp4";
            String output = Path.of(appProperties.getVideo().getOutputDir(), fileName).toString();

            ProcessBuilder pb = new ProcessBuilder(
                    ffmpegPath,
                    "-f", "lavfi",
                    "-i", "color=c=blue:s=1280x720:d=10",
                    "-vf", "drawtext=text=Top Story:fontcolor=white:fontsize=48:x=(w-text_w)/2:y=(h-text_h)/2",
                    "-y",
                    output
            );

            pb.redirectErrorStream(true);
            Process process = pb.start();
            int exit = process.waitFor();

            if (exit != 0) {
                throw new RuntimeException("FFmpeg failed with exit code: " + exit);
            }

            return new File(output).getAbsolutePath();

        } catch (Exception e) {
            throw new RuntimeException("Video generation failed: " + e.getMessage(), e);
        }
    }
}
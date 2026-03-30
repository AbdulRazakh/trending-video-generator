package com.agent.video.generator.trendsvideo.service;

import com.agent.video.generator.trendsvideo.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class VideoAssemblyService {

    private final AppProperties appProperties;

    public String concatenateScenes(List<String> scenePaths) throws IOException, InterruptedException {
        String outputDir = appProperties.getVideo().getOutputDir();
        Files.createDirectories(Path.of(outputDir));

        String concatListPath = Path.of(outputDir, "concat_" + UUID.randomUUID() + ".txt").toString();

        try (BufferedWriter writer = Files.newBufferedWriter(Path.of(concatListPath))) {
            for (String scenePath : scenePaths) {
                writer.write("file '" + scenePath.replace("'", "'\\''") + "'");
                writer.newLine();
            }
        }

        String finalOutput = Path.of(outputDir, "video_" + UUID.randomUUID() + ".mp4").toString();
        String ffmpeg = appProperties.getVideo().getFfmpegPath();

        ProcessBuilder pb = new ProcessBuilder(
                ffmpeg,
                "-f", "concat",
                "-safe", "0",
                "-i", concatListPath,
                "-c:v", "libx264",
                "-preset", "fast",
                "-crf", "22",
                "-c:a", "aac",
                "-b:a", "128k",
                "-movflags", "+faststart",
                "-pix_fmt", "yuv420p",
                "-y", finalOutput
        );

        pb.redirectErrorStream(true);
        Process process = pb.start();
        String output = new String(process.getInputStream().readAllBytes());
        int exit = process.waitFor();

        Files.deleteIfExists(Path.of(concatListPath));

        if (exit != 0) {
            log.error("FFmpeg concat failed:\n{}", output);
            throw new RuntimeException("Video concat failed, exit: " + exit);
        }

        log.info("Final video assembled: {} ({} scenes)", finalOutput, scenePaths.size());
        return finalOutput;
    }
}
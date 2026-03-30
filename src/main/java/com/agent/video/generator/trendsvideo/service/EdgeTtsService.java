package com.agent.video.generator.trendsvideo.service;

import com.agent.video.generator.trendsvideo.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class EdgeTtsService {

    private final AppProperties appProperties;

    public String generateAudio(String narrationText, String workspaceDir) throws IOException, InterruptedException {
        Files.createDirectories(Path.of(workspaceDir));

        String audioPath = Path.of(workspaceDir, "audio_" + UUID.randomUUID() + ".mp3").toString();
        AppProperties.Edgetts cfg = appProperties.getEdgetts();

        // Use full executable path — fixes Windows PATH issue
        String executable = cfg.getExecutablePath() != null ? cfg.getExecutablePath() : "edge-tts";

        String textFile = Path.of(workspaceDir, "narration_" + UUID.randomUUID() + ".txt").toString();
        Files.writeString(Path.of(textFile), narrationText);

        ProcessBuilder pb = new ProcessBuilder(
                executable,
                "--voice", cfg.getVoice(),
                "--rate", cfg.getRate(),
                "--volume", cfg.getVolume(),
                "--file", textFile,
                "--write-media", audioPath
        );
        pb.redirectErrorStream(true);
        Process process = pb.start();

        String output = new String(process.getInputStream().readAllBytes());
        int exit = process.waitFor();

        Files.deleteIfExists(Path.of(textFile));

        if (exit != 0) {
            throw new RuntimeException("edge-tts failed (exit " + exit + "): " + output);
        }

        log.info("Edge-TTS audio generated: {}", audioPath);
        return audioPath;
    }
}
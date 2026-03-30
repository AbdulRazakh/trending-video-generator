package com.agent.video.generator.trendsvideo.service;

import com.agent.video.generator.trendsvideo.config.AppProperties;
import com.agent.video.generator.trendsvideo.dto.VideoScriptJson;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SceneAssemblyService {

    private final AppProperties appProperties;
    private final EdgeTtsService edgeTtsService;
    private final UnsplashService unsplashService;

    public List<String> assembleSceneClips(VideoScriptJson script) throws Exception {
        String workspaceBase = appProperties.getVideo().getWorkspaceDir();
        if (workspaceBase == null || workspaceBase.isBlank()) {
            throw new RuntimeException("app.video.workspaceDir is not configured in application.yml");
        }
        String workspaceDir = Path.of(workspaceBase, "job_" + UUID.randomUUID()).toString();
        Files.createDirectories(Path.of(workspaceDir));

        List<String> sceneClips = new ArrayList<>();
        int sceneIndex = 0;

        for (VideoScriptJson.SceneJson scene : script.getScenes()) {
            log.info("Assembling scene {}: {}", sceneIndex + 1, scene.getHeading());
            String clipPath = assembleOneScene(scene, workspaceDir, sceneIndex);
            sceneClips.add(clipPath);
            sceneIndex++;
        }

        return sceneClips;
    }

    private String assembleOneScene(VideoScriptJson.SceneJson scene, String workspaceDir, int index)
            throws IOException, InterruptedException {

        // 1. TTS narration via edge-tts
        String audioPath = edgeTtsService.generateAudio(scene.getNarration(), workspaceDir);

        // 2. Background image from Unsplash
        String imagePath = unsplashService.downloadImage(scene.getImageHints(), workspaceDir);

        // 3. Ken Burns + text overlay via FFmpeg
        String outputPath = Path.of(workspaceDir, "scene_" + index + "_" + UUID.randomUUID() + ".mp4").toString();
        applyKenBurnsWithText(imagePath, audioPath, scene, outputPath);

        log.info("Scene {} assembled: {}", index + 1, outputPath);
        return outputPath;
    }

    private void applyKenBurnsWithText(String imagePath, String audioPath,
                                       VideoScriptJson.SceneJson scene, String outputPath)
            throws IOException, InterruptedException {

        int duration = scene.getDurationSeconds();
        String ffmpeg = appProperties.getVideo().getFfmpegPath();

        String heading = escapeFFmpegText(scene.getHeading().toUpperCase());
        String narration = escapeFFmpegText(truncate(scene.getNarration(), 80));

        // Ken Burns: slow zoom in over duration
        String zoompanFilter =
                "scale=8000:-1," +
                        "zoompan=z='min(zoom+0.0015,1.1)':x='iw/2-(iw/zoom/2)':y='ih/2-(ih/zoom/2)'" +
                        ":d=" + (duration * 25) + ":s=1080x1920:fps=25";

        // Heading at top — removed fontweight, using font=Arial bold style via fontfile or just Arial
        String headingFilter =
                "drawtext=text='" + heading + "'" +
                        ":fontsize=52:fontcolor=white:x=(w-text_w)/2:y=120" +
                        ":box=1:boxcolor=black@0.55:boxborderw=14";

        // Narration subtitle at bottom
        String subtitleFilter =
                "drawtext=text='" + narration + "'" +
                        ":fontsize=34:fontcolor=white:x=(w-text_w)/2:y=h-200" +
                        ":box=1:boxcolor=black@0.6:boxborderw=12";

        String filterComplex = zoompanFilter + "," + headingFilter + "," + subtitleFilter;

        ProcessBuilder pb = new ProcessBuilder(
                ffmpeg,
                "-loop", "1",
                "-i", imagePath,
                "-i", audioPath,
                "-filter_complex", filterComplex,
                "-c:v", "libx264",
                "-preset", "fast",
                "-crf", "23",
                "-c:a", "aac",
                "-b:a", "128k",
                "-t", String.valueOf(duration),
                "-pix_fmt", "yuv420p",
                "-shortest",
                "-y", outputPath
        );

        pb.redirectErrorStream(true);
        Process process = pb.start();
        String ffmpegOutput = new String(process.getInputStream().readAllBytes());
        int exit = process.waitFor();

        if (exit != 0) {
            log.error("FFmpeg scene assembly failed:\n{}", ffmpegOutput);
            throw new RuntimeException("FFmpeg failed for scene, exit: " + exit);
        }
    }

    private String escapeFFmpegText(String text) {
        if (text == null) return "";
        return text
                .replace("\\", "\\\\")
                .replace("'", "\u2019")   // replace apostrophe with curly quote
                .replace(":", "\\:")
                .replace("%", "\\%")
                .replace("\n", " ");
    }

    private String truncate(String text, int maxChars) {
        if (text == null) return "";
        if (text.length() <= maxChars) return text;
        int cut = text.lastIndexOf(' ', maxChars);
        return (cut > 0 ? text.substring(0, cut) : text.substring(0, maxChars)) + "...";
    }
}
package com.agent.video.generator.trendsvideo.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class VideoScriptJson {

    @NotBlank
    private String title;

    @NotBlank
    private String language;

    @NotBlank
    private String narrationStyle;

    @Valid
    @NotEmpty
    private List<SceneJson> scenes;

    @Data
    public static class SceneJson {
        @NotBlank
        private String heading;

        @NotBlank
        private String narration;

        @NotBlank
        private String visualPrompt;

        private int durationSeconds;

        private List<String> imageHints;
    }
}
package com.agent.video.generator.trendsvideo.service;

import org.springframework.stereotype.Service;

import java.io.File;

@Service
public class VideoValidationService {

    public void validate(String videoPath) {
        File file = new File(videoPath);
        if (!file.exists()) {
            throw new IllegalArgumentException("Video file does not exist");
        }
        if (file.length() < 1024) {
            throw new IllegalArgumentException("Video file is too small, likely invalid");
        }
        if (!videoPath.endsWith(".mp4")) {
            throw new IllegalArgumentException("Only mp4 is allowed");
        }
    }
}
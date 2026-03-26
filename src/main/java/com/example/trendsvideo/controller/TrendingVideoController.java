package com.example.trendsvideo.controller;

import com.example.trendsvideo.service.TrendingVideoPipelineService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/trends-video")
@RequiredArgsConstructor
public class TrendingVideoController {

    private final TrendingVideoPipelineService pipelineService;
    @GetMapping("/test")
    public ResponseEntity<String> test() {
        return ResponseEntity.ok("Controller is working");
    }
    @PostMapping("/run")
    public ResponseEntity<String> run() {
        try {
            pipelineService.runPipeline();
            return ResponseEntity.ok("Pipeline completed successfully");
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body("Pipeline failed: " + e.getMessage());
        }
    }
}
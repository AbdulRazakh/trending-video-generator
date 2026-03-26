package com.example.trendsvideo.scheduler;

import com.example.trendsvideo.service.TrendingVideoPipelineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class VideoScheduler {

    private final TrendingVideoPipelineService pipelineService;

    @Scheduled(cron = "0 0 */6 * * *")
    public void runEvery6Hours() {
        try {
            pipelineService.runPipeline();
        } catch (Exception e) {
            log.error("Scheduled pipeline failed", e);
        }
    }
}
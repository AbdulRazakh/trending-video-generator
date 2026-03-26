package com.agent.video.generator.trendsvideo.repository;

import com.agent.video.generator.trendsvideo.entity.GeneratedVideo;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GeneratedVideoRepository extends JpaRepository<GeneratedVideo, Long> {
}
package com.agent.video.generator.trendsvideo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "generated_video")
@Getter
@Setter
public class GeneratedVideo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String topicTitle;

    @Column(length = 5000)
    private String scriptJson;

    private String videoPath;
    private String thumbnailPath;
    private String uploadStatus;
    private String portalVideoId;
    private String portalUrl;
    private LocalDateTime createdAt;
}
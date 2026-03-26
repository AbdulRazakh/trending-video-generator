package com.example.portal.controller;

import com.example.trendsvideo.dto.PortalUploadResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/videos")
public class PortalVideoUploadController {

    @PostMapping("/upload")
    public ResponseEntity<PortalUploadResponse> upload(
            @RequestParam("title") String title,
            @RequestParam("language") String language,
            @RequestParam("visibility") String visibility,
            @RequestParam("video") MultipartFile video
    ) {
        PortalUploadResponse response = new PortalUploadResponse();
        response.setVideoId("VID-" + System.currentTimeMillis());
        response.setStatus("UPLOADED");
        response.setUrl("http://localhost:9090/watch/" + response.getVideoId());

        return ResponseEntity.ok(response);
    }
}
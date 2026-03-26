package com.agent.video.generator.trendsvideo.service;

import com.agent.video.generator.trendsvideo.config.AppProperties;
import com.agent.video.generator.trendsvideo.dto.PortalUploadResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.io.File;

@Service
@RequiredArgsConstructor
public class PortalUploadService {

    private final AppProperties appProperties;
    private final RestTemplate restTemplate = new RestTemplate();

    public PortalUploadResponse uploadVideo(File videoFile, String title) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.set("X-API-KEY", appProperties.getPortal().getApiKey());

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("title", title);
        body.add("language", "en");
        body.add("visibility", "public");
        body.add("video", new FileSystemResource(videoFile));

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        ResponseEntity<PortalUploadResponse> response = restTemplate.exchange(
                appProperties.getPortal().getUploadUrl(),
                HttpMethod.POST,
                requestEntity,
                PortalUploadResponse.class
        );

        return response.getBody();
    }
}
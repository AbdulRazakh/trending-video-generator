package com.example.trendsvideo.client;

import com.example.trendsvideo.dto.PortalUploadResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;

@FeignClient(name = "portalUploadClient", url = "${app.portal.uploadUrl}")
public interface PortalUploadClient {

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    PortalUploadResponse upload();
}
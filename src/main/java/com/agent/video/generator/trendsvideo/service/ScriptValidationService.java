package com.agent.video.generator.trendsvideo.service;

import com.agent.video.generator.trendsvideo.dto.VideoScriptJson;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ScriptValidationService {

    private final Validator validator;

    public void validate(VideoScriptJson script) {
        Set<ConstraintViolation<VideoScriptJson>> violations = validator.validate(script);
        if (!violations.isEmpty()) {
            String msg = violations.stream()
                    .map(v -> v.getPropertyPath() + " " + v.getMessage())
                    .collect(Collectors.joining(", "));
            throw new IllegalArgumentException("Invalid script JSON: " + msg);
        }
    }
}
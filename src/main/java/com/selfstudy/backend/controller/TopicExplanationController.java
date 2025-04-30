package com.selfstudy.backend.controller;

import com.selfstudy.backend.dto.ApiResponse;
import com.selfstudy.backend.dto.TopicExplanationRequest;
import com.selfstudy.backend.service.TopicExplanationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/topics")
@RequiredArgsConstructor
@Slf4j
public class TopicExplanationController {

    private final TopicExplanationService topicExplanationService;

    @PostMapping("/{id}/ask")
    public ResponseEntity<ApiResponse<String>> askQuestion(
            @PathVariable("id") Long topicId,
            @Valid @RequestBody TopicExplanationRequest request) {
        
        log.info("Received question for topic {}: {}", topicId, request.getQuestion());
        
        try {
            String explanation = topicExplanationService.generateExplanation(topicId, request);
            return ResponseEntity.ok(ApiResponse.success("Explanation generated successfully", explanation));
        } catch (Exception e) {
            log.error("Error generating explanation: {}", e.getMessage(), e);
            return ResponseEntity
                    .badRequest()
                    .body(ApiResponse.error("Error generating explanation: " + e.getMessage()));
        }
    }
}

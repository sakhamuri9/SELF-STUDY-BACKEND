package com.selfstudy.backend.controller;

import com.selfstudy.backend.dto.ApiResponse;
import com.selfstudy.backend.dto.VoiceNarrationRequest;
import com.selfstudy.backend.exception.ResourceNotFoundException;
import com.selfstudy.backend.service.VoiceNarrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
@RequestMapping("/audio")
@RequiredArgsConstructor
@Slf4j
public class VoiceNarrationController {

    private final VoiceNarrationService voiceNarrationService;

    /**
     * Generate audio for a topic's content
     */
    @PostMapping("/topics/{topicId}")
    public ResponseEntity<ApiResponse<String>> generateTopicAudio(
            @PathVariable Long topicId,
            @RequestBody(required = false) VoiceNarrationRequest request) {
        
        String voiceId = request != null && request.getVoiceId() != null ? 
                request.getVoiceId() : null;
        
        try {
            String audioPath = voiceNarrationService.generateTopicAudio(topicId, voiceId);
            return ResponseEntity.ok(new ApiResponse<>(true, "Audio generated successfully", audioPath));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error generating topic audio: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(new ApiResponse<>(false, "Error generating audio: " + e.getMessage(), null));
        }
    }

    /**
     * Generate audio for a summary's content
     */
    @PostMapping("/summaries/{summaryId}")
    public ResponseEntity<ApiResponse<String>> generateSummaryAudio(
            @PathVariable Long summaryId,
            @RequestBody(required = false) VoiceNarrationRequest request) {
        
        String voiceId = request != null && request.getVoiceId() != null ? 
                request.getVoiceId() : null;
        
        try {
            String audioPath = voiceNarrationService.generateSummaryAudio(summaryId, voiceId);
            return ResponseEntity.ok(new ApiResponse<>(true, "Audio generated successfully", audioPath));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error generating summary audio: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(new ApiResponse<>(false, "Error generating audio: " + e.getMessage(), null));
        }
    }

    /**
     * Stream audio file
     */
    @GetMapping("/stream/{fileName:.+}")
    public ResponseEntity<Resource> streamAudio(@PathVariable String fileName) {
        try {
            Path audioPath = Paths.get(voiceNarrationService.getAudioStorageDir(), fileName);
            
            if (!Files.exists(audioPath)) {
                return ResponseEntity.notFound().build();
            }
            
            Resource resource = voiceNarrationService.loadAudioAsResource(audioPath.toString());
            
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("audio/mpeg"))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"")
                    .body(resource);
        } catch (Exception e) {
            log.error("Error streaming audio: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Get available voices
     */
    @GetMapping("/voices")
    public ResponseEntity<ApiResponse<Object>> getAvailableVoices() {
        try {
            return ResponseEntity.ok(new ApiResponse<>(true, "Available voices retrieved successfully", 
                    voiceNarrationService.getAvailableVoices()));
        } catch (Exception e) {
            log.error("Error retrieving available voices: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(new ApiResponse<>(false, "Error retrieving available voices: " + e.getMessage(), null));
        }
    }
}

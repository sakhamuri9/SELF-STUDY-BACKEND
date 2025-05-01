package com.selfstudy.backend.controller;

import com.selfstudy.backend.dto.ApiResponse;
import com.selfstudy.backend.model.Topic;
import com.selfstudy.backend.service.NlpTopicExtractionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Controller for NLP-based topic extraction operations
 */
@RestController
@RequestMapping("/nlp-topics")
@RequiredArgsConstructor
@Slf4j
public class NlpTopicExtractionController {

    private final NlpTopicExtractionService nlpTopicExtractionService;

    /**
     * Trigger NLP-based topic extraction for a document
     * @param documentId ID of the document to extract topics from
     * @return Response with status of the extraction process
     */
    @PostMapping("/extract/{documentId}")
    public ResponseEntity<ApiResponse<String>> extractTopics(@PathVariable Long documentId) {
        log.info("Received request to extract topics using NLP for document: {}", documentId);
        
        CompletableFuture<List<Topic>> future = nlpTopicExtractionService.extractTopicsFromDocument(documentId);
        
        return ResponseEntity.ok(new ApiResponse<>(
                true, 
                "NLP-based topic extraction started for document: " + documentId,
                null
        ));
    }
    
    /**
     * Get all topics for a document extracted using NLP-based approach
     * @param documentId ID of the document
     * @return List of topics
     */
    @GetMapping("/document/{documentId}")
    public ResponseEntity<ApiResponse<List<Topic>>> getTopicsByDocument(@PathVariable Long documentId) {
        log.info("Fetching NLP-extracted topics for document: {}", documentId);
        
        List<Topic> topics = nlpTopicExtractionService.getTopicsByDocument(documentId);
        
        return ResponseEntity.ok(new ApiResponse<>(
                true,
                "Retrieved " + topics.size() + " NLP-extracted topics for document: " + documentId,
                topics
        ));
    }
    
    /**
     * Compare extraction results between traditional and NLP-based approaches
     * @param documentId ID of the document
     * @return Comparison results
     */
    @GetMapping("/compare/{documentId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> compareExtractionResults(@PathVariable Long documentId) {
        log.info("Comparing extraction results for document: {}", documentId);
        
        Map<String, Object> comparisonResults = nlpTopicExtractionService.compareExtractionResults(documentId);
        
        return ResponseEntity.ok(new ApiResponse<>(
                true,
                "Compared extraction results for document: " + documentId,
                comparisonResults
        ));
    }
}

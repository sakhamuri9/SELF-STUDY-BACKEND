package com.selfstudy.backend.controller;

import com.selfstudy.backend.model.TopicSummary;
import com.selfstudy.backend.service.TopicSummaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/summaries")
@RequiredArgsConstructor
@Slf4j
public class TopicSummaryController {

    private final TopicSummaryService topicSummaryService;

    @GetMapping("/topic/{topicId}")
    public ResponseEntity<List<TopicSummary>> getSummariesByTopicId(@PathVariable Long topicId) {
        log.info("Fetching summaries for topic: {}", topicId);
        List<TopicSummary> summaries = topicSummaryService.getSummariesForTopic(topicId);
        return ResponseEntity.ok(summaries);
    }

    @GetMapping("/topic/{topicId}/type/{type}")
    public ResponseEntity<TopicSummary> getSummaryByTopicAndType(
            @PathVariable Long topicId,
            @PathVariable String type) {
        log.info("Fetching {} summary for topic: {}", type, topicId);
        TopicSummary.SummaryType summaryType = TopicSummary.SummaryType.valueOf(type.toUpperCase());
        TopicSummary summary = topicSummaryService.getSummaryByTopicAndType(topicId, summaryType);
        return ResponseEntity.ok(summary);
    }

    @PostMapping("/generate/topic/{topicId}")
    public ResponseEntity<String> generateSummariesForTopic(@PathVariable Long topicId) {
        log.info("Generating summaries for topic: {}", topicId);
        CompletableFuture<List<TopicSummary>> future = topicSummaryService.generateSummariesForTopic(topicId);
        return ResponseEntity.ok("Summary generation started for topic: " + topicId);
    }

    @PostMapping("/generate/document/{documentId}")
    public ResponseEntity<String> generateSummariesForDocument(@PathVariable Long documentId) {
        log.info("Generating summaries for all topics in document: {}", documentId);
        CompletableFuture<List<TopicSummary>> future = topicSummaryService.generateSummariesForDocument(documentId);
        return ResponseEntity.ok("Summary generation started for all topics in document: " + documentId);
    }
}

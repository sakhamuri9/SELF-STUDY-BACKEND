package com.selfstudy.backend.controller;

import com.selfstudy.backend.model.Topic;
import com.selfstudy.backend.repository.TopicRepository;
import com.selfstudy.backend.service.TopicExtractionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/topics")
@RequiredArgsConstructor
@Slf4j
public class TopicController {

    private final TopicRepository topicRepository;
    private final TopicExtractionService topicExtractionService;

    @GetMapping("/document/{documentId}")
    public ResponseEntity<List<Topic>> getTopicsByDocumentId(@PathVariable Long documentId) {
        log.info("Fetching topics for document: {}", documentId);
        List<Topic> topics = topicRepository.findByDocumentId(documentId);
        return ResponseEntity.ok(topics);
    }

    @GetMapping("/document/{documentId}/root")
    public ResponseEntity<List<Topic>> getRootTopicsByDocumentId(@PathVariable Long documentId) {
        log.info("Fetching root topics for document: {}", documentId);
        List<Topic> topics = topicRepository.findByDocumentIdAndParentIsNull(documentId);
        return ResponseEntity.ok(topics);
    }

    @GetMapping("/parent/{parentId}")
    public ResponseEntity<List<Topic>> getTopicsByParentId(@PathVariable Long parentId) {
        log.info("Fetching topics for parent: {}", parentId);
        List<Topic> topics = topicRepository.findByParentId(parentId);
        return ResponseEntity.ok(topics);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Topic> getTopicById(@PathVariable Long id) {
        log.info("Fetching topic: {}", id);
        Topic topic = topicRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Topic not found with id: " + id));
        return ResponseEntity.ok(topic);
    }

    @PostMapping("/extract/{documentId}")
    public ResponseEntity<String> extractTopics(@PathVariable Long documentId) {
        log.info("Manually triggering topic extraction for document: {}", documentId);
        CompletableFuture<List<Topic>> future = topicExtractionService.extractTopicsFromDocument(documentId);
        return ResponseEntity.ok("Topic extraction started for document: " + documentId);
    }
}

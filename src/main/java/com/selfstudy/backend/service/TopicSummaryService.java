package com.selfstudy.backend.service;

import com.selfstudy.backend.model.Topic;
import com.selfstudy.backend.model.TopicSummary;
import com.selfstudy.backend.repository.TopicRepository;
import com.selfstudy.backend.repository.TopicSummaryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class TopicSummaryService {

    private final TopicRepository topicRepository;
    private final TopicSummaryRepository topicSummaryRepository;

    @Async
    public CompletableFuture<List<TopicSummary>> generateSummariesForTopic(Long topicId) {
        log.info("Generating summaries for topic: {}", topicId);
        
        Topic topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new RuntimeException("Topic not found with id: " + topicId));
        
        List<TopicSummary> summaries = new ArrayList<>();
        
        try {
            TopicSummary basicSummary = generateOrUpdateSummary(topic, TopicSummary.SummaryType.BASIC);
            summaries.add(basicSummary);
            
            TopicSummary detailedSummary = generateOrUpdateSummary(topic, TopicSummary.SummaryType.DETAILED);
            summaries.add(detailedSummary);
            
            TopicSummary childFriendlySummary = generateOrUpdateSummary(topic, TopicSummary.SummaryType.CHILD_FRIENDLY);
            summaries.add(childFriendlySummary);
            
            log.info("Successfully generated all summaries for topic: {}", topicId);
            return CompletableFuture.completedFuture(summaries);
            
        } catch (Exception e) {
            log.error("Error generating summaries for topic: {}", e.getMessage(), e);
            return CompletableFuture.failedFuture(e);
        }
    }
    
    @Transactional
    public TopicSummary generateOrUpdateSummary(Topic topic, TopicSummary.SummaryType type) {
        log.info("Generating {} summary for topic: {}", type, topic.getId());
        
        TopicSummary summary = topicSummaryRepository.findByTopicIdAndType(topic.getId(), type)
                .orElse(new TopicSummary());
        
        summary.setTopic(topic);
        summary.setType(type);
        summary.setStatus(TopicSummary.GenerationStatus.PROCESSING);
        topicSummaryRepository.save(summary);
        
        try {
            String content = "";
            String examples = "";
            
            switch (type) {
                case BASIC:
                    content = generateBasicSummary(topic);
                    examples = generateBasicExamples(topic);
                    break;
                case DETAILED:
                    content = generateDetailedSummary(topic);
                    examples = generateDetailedExamples(topic);
                    break;
                case CHILD_FRIENDLY:
                    content = generateChildFriendlySummary(topic);
                    examples = generateChildFriendlyExamples(topic);
                    break;
            }
            
            summary.setContent(content);
            summary.setExamples(examples);
            summary.setStatus(TopicSummary.GenerationStatus.COMPLETED);
            
        } catch (Exception e) {
            log.error("Error generating {} summary for topic {}: {}", type, topic.getId(), e.getMessage(), e);
            summary.setStatus(TopicSummary.GenerationStatus.FAILED);
        }
        
        return topicSummaryRepository.save(summary);
    }
    
    private String generateBasicSummary(Topic topic) {
        String content = topic.getContent();
        if (content == null || content.isEmpty()) {
            return "No content available for this topic.";
        }
        
        String[] sentences = content.split("[.!?]\\s+");
        if (sentences.length <= 2) {
            return content;
        }
        
        return sentences[0] + ". " + sentences[1] + ".";
    }
    
    private String generateDetailedSummary(Topic topic) {
        String content = topic.getContent();
        if (content == null || content.isEmpty()) {
            return "No detailed content available for this topic.";
        }
        
        return "This topic covers " + topic.getTitle() + ". " + content;
    }
    
    private String generateChildFriendlySummary(Topic topic) {
        String content = topic.getContent();
        if (content == null || content.isEmpty()) {
            return "Nothing to read here yet!";
        }
        
        String[] sentences = content.split("[.!?]\\s+");
        if (sentences.length == 0) {
            return "This is about " + topic.getTitle() + "!";
        }
        
        String firstSentence = sentences[0].replaceAll("\\b\\w{10,}\\b", "thing");
        return "Let's learn about " + topic.getTitle() + "! " + firstSentence + ".";
    }
    
    private String generateBasicExamples(Topic topic) {
        return "Example: Consider how " + topic.getTitle() + " is used in practice.";
    }
    
    private String generateDetailedExamples(Topic topic) {
        return "Example 1: " + topic.getTitle() + " can be applied in various scenarios.\n\n" +
               "Example 2: Here's how professionals use " + topic.getTitle() + " in their work.";
    }
    
    private String generateChildFriendlyExamples(Topic topic) {
        return "Fun example: Imagine you're playing with " + topic.getTitle() + "!";
    }
    
    public List<TopicSummary> getSummariesForTopic(Long topicId) {
        return topicSummaryRepository.findByTopicId(topicId);
    }
    
    public TopicSummary getSummaryByTopicAndType(Long topicId, TopicSummary.SummaryType type) {
        return topicSummaryRepository.findByTopicIdAndType(topicId, type)
                .orElseThrow(() -> new RuntimeException("Summary not found for topic: " + topicId + " and type: " + type));
    }
    
    @Async
    public CompletableFuture<List<TopicSummary>> generateSummariesForDocument(Long documentId) {
        log.info("Generating summaries for all topics in document: {}", documentId);
        
        List<Topic> topics = topicRepository.findByDocumentId(documentId);
        List<TopicSummary> allSummaries = new ArrayList<>();
        
        for (Topic topic : topics) {
            try {
                List<TopicSummary> topicSummaries = generateSummariesForTopic(topic.getId()).get();
                allSummaries.addAll(topicSummaries);
            } catch (Exception e) {
                log.error("Error generating summaries for topic {}: {}", topic.getId(), e.getMessage(), e);
            }
        }
        
        log.info("Completed generating summaries for document: {}", documentId);
        return CompletableFuture.completedFuture(allSummaries);
    }
}

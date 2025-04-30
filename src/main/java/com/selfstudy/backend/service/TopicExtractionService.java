package com.selfstudy.backend.service;

import com.selfstudy.backend.model.Document;
import com.selfstudy.backend.model.Topic;
import com.selfstudy.backend.repository.DocumentRepository;
import com.selfstudy.backend.repository.TopicRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class TopicExtractionService {

    private final TopicRepository topicRepository;
    private final DocumentRepository documentRepository;

    private static final Pattern CHAPTER_PATTERN = Pattern.compile("(?m)^\\s*(Chapter|CHAPTER)\\s+(\\d+|[IVX]+)\\s*[.:)]?\\s*(.+)$");
    private static final Pattern HEADING_PATTERN = Pattern.compile("(?m)^\\s*(\\d+|[IVX]+)\\s*[.:)]\\s*(.+)$");
    private static final Pattern SUBHEADING_PATTERN = Pattern.compile("(?m)^\\s*(\\d+\\.\\d+|\\d+\\.\\d+\\.\\d+|[a-z]\\.|[A-Z]\\.|[•\\*\\-])\\s*(.+)$");
    private static final Pattern TITLE_PATTERN = Pattern.compile("(?m)^\\s*([A-Z][A-Za-z\\s]+)\\s*$");
    private static final Pattern INTERFACE_PATTERN = Pattern.compile("(?m)^\\s*([A-Z][A-Za-z]+)\\s+interface\\s*$");
    private static final Pattern CLASS_PATTERN = Pattern.compile("(?m)^\\s*([A-Z][A-Za-z]+)\\s+class(es)?\\s*$");

    @Async
    public CompletableFuture<List<Topic>> extractTopicsFromDocument(Long documentId) {
        log.info("Starting topic extraction for document: {}", documentId);
        
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Document not found with id: " + documentId));
        
        if (document.getExtractedText() == null || document.getExtractedText().isEmpty()) {
            log.error("Document {} has no extracted text", documentId);
            document.setTopicExtractionStatus(Document.TopicExtractionStatus.FAILED);
            documentRepository.save(document);
            return CompletableFuture.completedFuture(new ArrayList<>());
        }
        
        try {
            document.setTopicExtractionStatus(Document.TopicExtractionStatus.PROCESSING);
            documentRepository.save(document);
            
            List<Topic> topics = extractTopics(document);
            
            document.setTopicExtractionStatus(Document.TopicExtractionStatus.COMPLETED);
            documentRepository.save(document);
            
            log.info("Completed topic extraction for document: {}, found {} topics", documentId, topics.size());
            return CompletableFuture.completedFuture(topics);
            
        } catch (Exception e) {
            log.error("Error extracting topics from document: {}", e.getMessage(), e);
            document.setTopicExtractionStatus(Document.TopicExtractionStatus.FAILED);
            documentRepository.save(document);
            return CompletableFuture.failedFuture(e);
        }
    }
    
    @Transactional
    public List<Topic> extractTopics(Document document) {
        String text = document.getExtractedText();
        List<Topic> allTopics = new ArrayList<>();
        
        String[] lines = text.split("\\n");
        
        Stack<Topic> topicStack = new Stack<>();
        Topic currentTopic = null;
        StringBuilder contentBuilder = new StringBuilder();
        
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            
            if (line.isEmpty()) {
                if (currentTopic != null) {
                    contentBuilder.append("\n");
                }
                continue;
            }
            
            Topic newTopic = null;
            
            Matcher chapterMatcher = CHAPTER_PATTERN.matcher(line);
            if (chapterMatcher.matches()) {
                String title = chapterMatcher.group(3);
                newTopic = createTopic(title, i, Topic.TopicType.CHAPTER, document);
            }
            
            if (newTopic == null) {
                Matcher interfaceMatcher = INTERFACE_PATTERN.matcher(line);
                if (interfaceMatcher.matches()) {
                    String title = interfaceMatcher.group(1) + " interface";
                    newTopic = createTopic(title, i, Topic.TopicType.HEADING, document);
                }
            }
            
            if (newTopic == null) {
                Matcher classMatcher = CLASS_PATTERN.matcher(line);
                if (classMatcher.matches()) {
                    String title = classMatcher.group(1) + " class";
                    newTopic = createTopic(title, i, Topic.TopicType.HEADING, document);
                }
            }
            
            if (newTopic == null) {
                Matcher headingMatcher = HEADING_PATTERN.matcher(line);
                if (headingMatcher.matches()) {
                    String title = headingMatcher.group(2);
                    newTopic = createTopic(title, i, Topic.TopicType.HEADING, document);
                }
            }
            
            if (newTopic == null) {
                Matcher subheadingMatcher = SUBHEADING_PATTERN.matcher(line);
                if (subheadingMatcher.matches()) {
                    String title = subheadingMatcher.group(2);
                    newTopic = createTopic(title, i, Topic.TopicType.SUBHEADING, document);
                }
            }
            
            if (newTopic == null) {
                Matcher titleMatcher = TITLE_PATTERN.matcher(line);
                if (titleMatcher.matches() && line.length() > 10 && line.length() < 100) {
                    String title = titleMatcher.group(1);
                    newTopic = createTopic(title, i, Topic.TopicType.HEADING, document);
                }
            }
            
            if (newTopic != null) {
                if (currentTopic != null) {
                    currentTopic.setContent(contentBuilder.toString().trim());
                    currentTopic.setEndPosition(i - 1);
                    topicRepository.save(currentTopic);
                    allTopics.add(currentTopic);
                }
                
                if (!topicStack.isEmpty()) {
                    Topic parent = findAppropriateParent(topicStack, newTopic);
                    if (parent != null) {
                        newTopic.setParent(parent);
                        if (parent.getLevel() != null) {
                            newTopic.setLevel(parent.getLevel() + 1);
                        } else {
                            newTopic.setLevel(1);
                        }
                    }
                } else {
                    newTopic.setLevel(1);
                }
                
                contentBuilder = new StringBuilder();
                contentBuilder.append(line).append("\n");
                
                topicStack.push(newTopic);
                currentTopic = newTopic;
            } else if (currentTopic != null) {
                contentBuilder.append(line).append("\n");
            } else {
                currentTopic = createTopic(document.getTitle(), 0, Topic.TopicType.CHAPTER, document);
                currentTopic.setLevel(1);
                topicStack.push(currentTopic);
                contentBuilder.append(line).append("\n");
            }
        }
        
        if (currentTopic != null) {
            currentTopic.setContent(contentBuilder.toString().trim());
            currentTopic.setEndPosition(lines.length - 1);
            topicRepository.save(currentTopic);
            allTopics.add(currentTopic);
        }
        
        return allTopics;
    }
    
    private Topic createTopic(String title, int position, Topic.TopicType type, Document document) {
        Topic topic = new Topic();
        topic.setTitle(title);
        topic.setStartPosition(position);
        topic.setType(type);
        topic.setDocument(document);
        topic.setLevel(1); // Set default level to prevent NullPointerException
        return topic;
    }
    
    private Topic findAppropriateParent(Stack<Topic> stack, Topic newTopic) {
        
        Stack<Topic> tempStack = new Stack<>();
        Topic parent = null;
        
        while (!stack.isEmpty()) {
            Topic potentialParent = stack.pop();
            tempStack.push(potentialParent);
            
            if (isAppropriateParent(potentialParent, newTopic)) {
                parent = potentialParent;
                break;
            }
        }
        
        while (!tempStack.isEmpty()) {
            stack.push(tempStack.pop());
        }
        
        return parent;
    }
    
    private boolean isAppropriateParent(Topic potentialParent, Topic child) {
        if (potentialParent.getLevel() != null && child.getLevel() != null) {
            if (child.getLevel() == potentialParent.getLevel() + 1) {
                return true;
            }
        }
        
        if (potentialParent.getType() == Topic.TopicType.CHAPTER && 
            (child.getType() == Topic.TopicType.HEADING || 
             child.getType() == Topic.TopicType.SUBHEADING || 
             child.getType() == Topic.TopicType.PARAGRAPH)) {
            return true;
        }
        
        if (potentialParent.getType() == Topic.TopicType.HEADING && 
            (child.getType() == Topic.TopicType.SUBHEADING || 
             child.getType() == Topic.TopicType.PARAGRAPH)) {
            return true;
        }
        
        if (potentialParent.getType() == Topic.TopicType.SUBHEADING && 
            child.getType() == Topic.TopicType.PARAGRAPH) {
            return true;
        }
        
        return false;
    }
}

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

    private static final Pattern CHAPTER_PATTERN = Pattern.compile("(?m)^\\s*(Chapter|CHAPTER|Section|SECTION)\\s+(\\d+|[IVX]+)\\s*[.:)]?\\s*(.+)$");
    private static final Pattern HEADING_PATTERN = Pattern.compile("(?m)^\\s*(\\d+|[IVX]+)\\s*[.:)]\\s*(.+)$");
    private static final Pattern SUBHEADING_PATTERN = Pattern.compile("(?m)^\\s*(\\d+\\.\\d+|\\d+\\.\\d+\\.\\d+|[a-z]\\.|[A-Z]\\.|[•\\*\\-]|\\*|\\+)\\s*(.+)$");
    private static final Pattern METHOD_PATTERN = Pattern.compile("(?m)^\\s*([a-zA-Z_][a-zA-Z0-9_]*\\s*\\([^)]*\\))\\s*$");
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
        
        String fullDocumentContent = document.getExtractedText();
        
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
                Matcher methodMatcher = METHOD_PATTERN.matcher(line);
                if (methodMatcher.matches()) {
                    String title = methodMatcher.group(1);
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
                    
                    extractTopicContext(currentTopic, fullDocumentContent, document.getTitle());
                    
                    topicRepository.save(currentTopic);
                    allTopics.add(currentTopic);
                }
                
                if (newTopic.getLevel() == null) {
                    newTopic.setLevel(1);
                }
                
                if (!topicStack.isEmpty()) {
                    Topic parent = findAppropriateParent(topicStack, newTopic);
                    if (parent != null) {
                        newTopic.setParent(parent);
                        if (parent.getLevel() == null) {
                            parent.setLevel(1);
                        }
                        newTopic.setLevel(parent.getLevel() + 1);
                    }
                }
                
                contentBuilder = new StringBuilder();
                contentBuilder.append(line).append("\n");
                
                topicStack.push(newTopic);
                currentTopic = newTopic;
            }else if (currentTopic != null) {
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
            
            extractTopicContext(currentTopic, fullDocumentContent, document.getTitle());
            
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
        if (potentialParent.getLevel() == null) {
            potentialParent.setLevel(1);
        }
        
        if (child.getLevel() == null) {
            child.setLevel(potentialParent.getLevel() + 1);
        }
        
        if (child.getLevel() == potentialParent.getLevel() + 1) {
            return true;
        }
        
        if (potentialParent.getType() == Topic.TopicType.CHAPTER && 
            (child.getType() == Topic.TopicType.HEADING || 
             child.getType() == Topic.TopicType.SUBHEADING || 
             child.getType() == Topic.TopicType.PARAGRAPH)) {
            child.setLevel(potentialParent.getLevel() + 1);
            return true;
        }
        
        if (potentialParent.getType() == Topic.TopicType.HEADING && 
            (child.getType() == Topic.TopicType.SUBHEADING || 
             child.getType() == Topic.TopicType.PARAGRAPH)) {
            child.setLevel(potentialParent.getLevel() + 1);
            return true;
        }
        
        if (potentialParent.getType() == Topic.TopicType.SUBHEADING && 
            child.getType() == Topic.TopicType.PARAGRAPH) {
            child.setLevel(potentialParent.getLevel() + 1);
            return true;
        }
        
        if (Math.abs(potentialParent.getStartPosition() - child.getStartPosition()) < 10 &&
            potentialParent.getType().ordinal() < child.getType().ordinal()) {
            child.setLevel(potentialParent.getLevel() + 1);
            return true;
        }
        
        return false;
    }
    
    /**
     * Extracts more comprehensive context for a topic by searching for its title in the document content
     * and extracting a larger portion of text around it.
     * 
     * @param topic The topic to extract context for
     * @param fullDocumentContent The full document content
     * @param documentTitle The document title
     */
    private void extractTopicContext(Topic topic, String fullDocumentContent, String documentTitle) {
        if (topic.getContent() == null || topic.getContent().trim().isEmpty() || 
            topic.getContent().length() < 100) {
            
            log.info("Extracting more context for topic: {}", topic.getTitle());
            
            String topicTitle = topic.getTitle();
            
            String titleNumber = "";
            Pattern numberPattern = Pattern.compile("\\s+(\\d+)\\s*$");
            Matcher numberMatcher = numberPattern.matcher(topicTitle);
            if (numberMatcher.find()) {
                titleNumber = numberMatcher.group(1);
                log.info("Found number in title: {} for topic: {}", titleNumber, topicTitle);
            }
            
            String cleanTitle = topicTitle.replaceAll("\\s+\\d+$", "").trim();
            log.info("Cleaned title for searching: '{}' from original '{}'", cleanTitle, topicTitle);
            
            String escapedTitle = Pattern.quote(cleanTitle);
            
            if (!titleNumber.isEmpty()) {
                String sectionPattern = "(?i)\\bSection\\s+" + titleNumber + "\\.\\s+" + escapedTitle + "\\b.*?(?=\\n\\s*\\n|\\n\\s*Section\\s+\\d+|\\n\\s*Chapter|\\n\\s*CHAPTER|$)";
                log.info("Trying section pattern: {}", sectionPattern);
                Pattern pattern = Pattern.compile(sectionPattern, Pattern.DOTALL);
                Matcher matcher = pattern.matcher(fullDocumentContent);
                
                if (matcher.find()) {
                    String sectionContent = matcher.group(0);
                    log.info("Found content using section pattern for topic: {} (length: {})", 
                            topicTitle, sectionContent.length());
                    
                    int maxLength = 5000;
                    if (sectionContent.length() > maxLength) {
                        sectionContent = sectionContent.substring(0, maxLength) + "...";
                    }
                    
                    topic.setContent(sectionContent.trim());
                    return;
                } else {
                    log.info("No match found with section pattern for topic: {}", topicTitle);
                }
                
                String sectionNumberPattern = "(?i)\\bSection\\s+" + titleNumber + "\\b.*?(?=\\n\\s*\\n|\\n\\s*Section\\s+\\d+|\\n\\s*Chapter|\\n\\s*CHAPTER|$)";
                log.info("Trying section number pattern: {}", sectionNumberPattern);
                Pattern sectionNumberPatternObj = Pattern.compile(sectionNumberPattern, Pattern.DOTALL);
                Matcher sectionNumberMatcher = sectionNumberPatternObj.matcher(fullDocumentContent);
                
                if (sectionNumberMatcher.find()) {
                    String sectionContent = sectionNumberMatcher.group(0);
                    log.info("Found content using section number pattern for topic: {} (length: {})", 
                            topicTitle, sectionContent.length());
                    
                    int maxLength = 5000;
                    if (sectionContent.length() > maxLength) {
                        sectionContent = sectionContent.substring(0, maxLength) + "...";
                    }
                    
                    topic.setContent(sectionContent.trim());
                    return;
                } else {
                    log.info("No match found with section number pattern for topic: {}", topicTitle);
                }
            }
            
            String fullTitlePattern = "(?i)\\b" + Pattern.quote(topicTitle) + "\\b.*?(?=\\n\\s*\\n|\\n\\s*\\d+\\.\\s|\\n\\s*Chapter|\\n\\s*CHAPTER|$)";
            log.info("Trying pattern with full title: {}", fullTitlePattern);
            Pattern fullPattern = Pattern.compile(fullTitlePattern, Pattern.DOTALL);
            Matcher fullMatcher = fullPattern.matcher(fullDocumentContent);
            
            String foundContent = null;
            if (fullMatcher.find()) {
                foundContent = fullMatcher.group(0);
                log.info("Found content using full title pattern for topic: {} (length: {})", 
                        topicTitle, foundContent.length());
                
                if (foundContent.length() < 100 && !titleNumber.isEmpty()) {
                    log.info("Content too short ({}), trying to find more comprehensive content", foundContent.length());
                    foundContent = null;
                }
            } else {
                log.info("No match found with full title pattern for topic: {}", topicTitle);
            }
            
            if (foundContent == null || foundContent.length() < 100) {
                List<Pattern> patterns = new ArrayList<>();
                
                patterns.add(Pattern.compile("(?i)\\b" + escapedTitle + "\\b.*?(?=\\n\\s*\\n|\\n\\s*\\d+\\.\\s|\\n\\s*Chapter|\\n\\s*CHAPTER|$)", Pattern.DOTALL));
                patterns.add(Pattern.compile("(?i)\\b" + escapedTitle.replaceAll("\\s+", "\\\\s+") + "\\b.*?(?=\\n\\s*\\n|\\n\\s*\\d+\\.\\s|\\n\\s*Chapter|\\n\\s*CHAPTER|$)", Pattern.DOTALL));
                
                if (!titleNumber.isEmpty()) {
                    patterns.add(Pattern.compile("(?i)\\b" + escapedTitle + "\\s+" + titleNumber + "\\b.*?(?=\\n\\s*\\n|\\n\\s*\\d+\\.\\s|\\n\\s*Chapter|\\n\\s*CHAPTER|$)", Pattern.DOTALL));
                    patterns.add(Pattern.compile("(?i)\\b" + titleNumber + "\\.\\s+" + escapedTitle + "\\b.*?(?=\\n\\s*\\n|\\n\\s*\\d+\\.\\s|\\n\\s*Chapter|\\n\\s*CHAPTER|$)", Pattern.DOTALL));
                    patterns.add(Pattern.compile("(?i)\\bSection\\s+" + titleNumber + "\\b.*?(?=\\n\\s*\\n|\\n\\s*\\d+\\.\\s|\\n\\s*Chapter|\\n\\s*CHAPTER|$)", Pattern.DOTALL));
                }
                
                patterns.add(Pattern.compile("(?i)" + escapedTitle + ".*?(?=\\n\\s*\\n|\\n\\s*\\d+\\.\\s|\\n\\s*Chapter|\\n\\s*CHAPTER|$)", Pattern.DOTALL));
                patterns.add(Pattern.compile("(?i)Section\\s+\\d+\\.\\s+" + escapedTitle + ".*?(?=\\n\\s*\\n|\\n\\s*\\d+\\.\\s|\\n\\s*Chapter|\\n\\s*CHAPTER|$)", Pattern.DOTALL));
                patterns.add(Pattern.compile("(?i)\\b\\d+\\.\\s+" + escapedTitle + ".*?(?=\\n\\s*\\n|\\n\\s*\\d+\\.\\s|\\n\\s*Chapter|\\n\\s*CHAPTER|$)", Pattern.DOTALL));
                
                for (Pattern pattern : patterns) {
                    log.info("Trying pattern: {}", pattern.pattern());
                    Matcher matcher = pattern.matcher(fullDocumentContent);
                    if (matcher.find()) {
                        String content = matcher.group(0);
                        log.info("Found content using pattern: {} for topic: {} (length: {})", 
                                pattern.pattern(), topicTitle, content.length());
                        
                        if (foundContent == null || content.length() > foundContent.length()) {
                            foundContent = content;
                            log.info("Using longer content: {} characters", foundContent.length());
                        }
                    } else {
                        log.info("No match found with pattern: {} for topic: {}", pattern.pattern(), topicTitle);
                    }
                }
            }
            
            if (foundContent != null && !foundContent.trim().isEmpty()) {
                int maxLength = 5000; // Increased from 2000 to capture more comprehensive content
                if (foundContent.length() > maxLength) {
                    foundContent = foundContent.substring(0, maxLength) + "...";
                }
                
                log.info("Setting content for topic: {} (length: {})", topic.getTitle(), foundContent.length());
                topic.setContent(foundContent.trim());
            } else {
                log.info("No content found for topic: {} using regex patterns, trying fallback methods", topicTitle);
                
                if (!titleNumber.isEmpty()) {
                    String sectionPattern = "\\b" + titleNumber + "(\\.\\d+)*\\s+" + escapedTitle + "\\b";
                    log.info("Trying section pattern: {}", sectionPattern);
                    Pattern pattern = Pattern.compile(sectionPattern, Pattern.CASE_INSENSITIVE);
                    Matcher matcher = pattern.matcher(fullDocumentContent);
                    
                    if (matcher.find()) {
                        int startPos = matcher.start();
                        int endPos = fullDocumentContent.indexOf("\n\n", startPos + matcher.group(0).length());
                        if (endPos == -1) {
                            endPos = fullDocumentContent.length();
                        }
                        
                        String sectionContent = fullDocumentContent.substring(startPos, endPos);
                        if (sectionContent.length() > 100) {
                            log.info("Found section content for topic: {} (length: {})", topic.getTitle(), sectionContent.length());
                            topic.setContent(sectionContent.trim());
                        } else {
                            log.info("Found section content too short for topic: {} (length: {})", topic.getTitle(), sectionContent.length());
                        }
                    } else {
                        log.info("No match found with section pattern for topic: {}", topicTitle);
                    }
                }
                
                if (topic.getContent() == null || topic.getContent().trim().isEmpty() || topic.getContent().length() < 100) {
                    String[] titleWords = cleanTitle.split("\\s+");
                    if (titleWords.length > 0) {
                        StringBuilder wordPatternBuilder = new StringBuilder("(?i)");
                        for (String word : titleWords) {
                            if (word.length() > 3) { // Only use significant words
                                wordPatternBuilder.append("\\b").append(Pattern.quote(word)).append("\\b.*?");
                            }
                        }
                        wordPatternBuilder.append("(?=\\n\\s*\\n|\\n\\s*\\d+\\.\\s|\\n\\s*Chapter|\\n\\s*CHAPTER|$)");
                        
                        String wordPattern = wordPatternBuilder.toString();
                        log.info("Trying word pattern: {}", wordPattern);
                        Pattern pattern = Pattern.compile(wordPattern, Pattern.DOTALL);
                        Matcher matcher = pattern.matcher(fullDocumentContent);
                        
                        if (matcher.find()) {
                            String wordContent = matcher.group(0);
                            if (wordContent.length() > 100) {
                                log.info("Found content using word pattern for topic: {} (length: {})", 
                                        topic.getTitle(), wordContent.length());
                                topic.setContent(wordContent.trim());
                            } else {
                                log.info("Found content too short using word pattern for topic: {} (length: {})", 
                                        topic.getTitle(), wordContent.length());
                            }
                        } else {
                            log.info("No match found with word pattern for topic: {}", topicTitle);
                        }
                    }
                }
                
                if (topic.getContent() == null || topic.getContent().trim().isEmpty() || topic.getContent().length() < 100) {
                    log.info("No content found using patterns for topic: {}, extracting from document position", topicTitle);
                    
                    String[] lines = fullDocumentContent.split("\n");
                    int startLine = Math.max(0, topic.getStartPosition() - 5);
                    int endLine = Math.min(lines.length - 1, topic.getEndPosition() + 20);
                    
                    StringBuilder positionContent = new StringBuilder();
                    for (int i = startLine; i <= endLine; i++) {
                        if (i < lines.length) {
                            positionContent.append(lines[i]).append("\n");
                        }
                    }
                    
                    String extractedContent = positionContent.toString().trim();
                    if (extractedContent.length() > 100) {
                        log.info("Extracted content from position for topic: {} (length: {})", 
                                topic.getTitle(), extractedContent.length());
                        topic.setContent(extractedContent);
                    } else {
                        log.info("Extracted content too short from position for topic: {} (length: {})", 
                                topic.getTitle(), extractedContent.length());
                    }
                }
            }
        }
    }
}

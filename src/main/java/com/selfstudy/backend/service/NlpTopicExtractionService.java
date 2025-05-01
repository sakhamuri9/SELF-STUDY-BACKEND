package com.selfstudy.backend.service;

import com.selfstudy.backend.model.Document;
import com.selfstudy.backend.model.Topic;
import com.selfstudy.backend.repository.DocumentRepository;
import com.selfstudy.backend.repository.TopicRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Service for extracting topics from documents using NLP-based approaches
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NlpTopicExtractionService {

    private final DocumentRepository documentRepository;
    private final TopicRepository topicRepository;
    
    @Autowired
    @Qualifier("maxContentLength")
    private int maxContentLength;
    
    @Autowired
    @Qualifier("minParagraphLength")
    private int minParagraphLength;
    
    private static final List<String> HEADING_TERMS = Arrays.asList(
            "chapter", "section", "introduction", "overview", "summary",
            "conclusion", "appendix", "reference", "bibliography", "glossary",
            "index", "table of contents", "preface", "foreword", "afterword"
    );
    
    private static final List<Pattern> SECTION_BOUNDARY_PATTERNS = Arrays.asList(
            Pattern.compile("^\\s*(?:CHAPTER|Chapter)\\s+\\d+.*$"),
            Pattern.compile("^\\s*\\d+\\.\\s+[A-Z].*$"),
            Pattern.compile("^\\s*\\d+\\.\\d+\\.\\s+[A-Z].*$")
    );
    
    @Async
    public CompletableFuture<List<Topic>> extractTopicsFromDocument(Long documentId) {
        log.info("Starting NLP-based topic extraction for document: {}", documentId);
        
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Document not found with id: " + documentId));
        
        List<Topic> topics = extractTopics(document);
        
        log.info("Completed NLP-based topic extraction for document: {}, found {} topics", documentId, topics.size());
        
        return CompletableFuture.completedFuture(topics);
    }
    
    public List<Topic> extractTopics(Document document) {
        String text = document.getExtractedText();
        
        String[] paragraphs = text.split("\\n\\s*\\n+");
        List<String> paragraphList = new ArrayList<>(Arrays.asList(paragraphs));
        
        paragraphList = paragraphList.stream()
                .filter(p -> p.trim().length() > minParagraphLength)
                .collect(Collectors.toList());
        
        List<Topic> topics = new ArrayList<>();
        Topic currentParent = null;
        int linePosition = 0;
        
        for (int i = 0; i < paragraphList.size(); i++) {
            String paragraph = paragraphList.get(i);
            String[] lines = paragraph.split("\\n");
            
            boolean isNewTopic = false;
            Topic newTopic = null;
            
            if (lines.length > 0) {
                String firstLine = lines[0].trim();
                
                for (Pattern pattern : SECTION_BOUNDARY_PATTERNS) {
                    Matcher headingMatcher = pattern.matcher(firstLine);
                    
                    if (headingMatcher.matches() && firstLine.length() > 5 && firstLine.length() < 100) {
                        String title = firstLine.trim();
                        title = title.replaceAll("^\\d+\\.?\\s*", "");
                        
                        final String finalTitle = title;
                        boolean containsHeadingTerm = HEADING_TERMS.stream()
                                .anyMatch(term -> finalTitle.toLowerCase().contains(term));
                        
                        if (containsHeadingTerm || isAllCaps(title) || isTitleCase(title)) {
                            newTopic = createTopic(title, linePosition, Topic.TopicType.HEADING, document);
                            isNewTopic = true;
                        }
                    }
                }
                
                if (!isNewTopic && firstLine.length() > 5 && firstLine.length() < 100) {
                    if (isAllCaps(firstLine) || isTitleCase(firstLine)) {
                        if (!firstLine.endsWith(".") || firstLine.split("\\s+").length <= 5) {
                            newTopic = createTopic(firstLine, linePosition, Topic.TopicType.HEADING, document);
                            isNewTopic = true;
                        }
                    }
                    
                    if (!isNewTopic) {
                        Pattern numberPattern = Pattern.compile("^\\s*(\\d+(?:\\.\\d+)*)\\s+(.+)$");
                        Matcher numberMatcher = numberPattern.matcher(firstLine);
                        
                        if (numberMatcher.matches()) {
                            String title = numberMatcher.group(2).trim();
                            if (title.length() > 5 && title.length() < 100) {
                                newTopic = createTopic(title, linePosition, Topic.TopicType.HEADING, document);
                                isNewTopic = true;
                            }
                        }
                    }
                    
                    if (!isNewTopic && isProbableSubheading(firstLine, i > 0 ? paragraphList.get(i - 1) : "")) {
                        newTopic = createTopic(firstLine, linePosition, Topic.TopicType.SUBHEADING, document);
                        isNewTopic = true;
                    }
                }
            }
            
            if (isNewTopic) {
                Topic parent = findAppropriateParent(topics, newTopic);
                if (parent != null) {
                    newTopic.setParent(parent);
                    newTopic.setLevel(parent.getLevel() + 1);
                } else {
                    newTopic.setLevel(1);
                }
                
                String context = extractTopicContext(paragraphList, i);
                newTopic.setContent(context);
                
                topics.add(newTopic);
                currentParent = newTopic;
            } else if (currentParent != null) {
                String existingContent = currentParent.getContent();
                if (existingContent == null) {
                    existingContent = "";
                }
                
                if (existingContent.length() < maxContentLength) {
                    String newContent = existingContent + "\n\n" + paragraph;
                    if (newContent.length() > maxContentLength) {
                        newContent = newContent.substring(0, maxContentLength);
                    }
                    currentParent.setContent(newContent);
                    topicRepository.save(currentParent);
                }
            }
            
            linePosition += paragraph.length() + 2; // +2 for the newlines
        }
        
        return topics;
    }
    
    private Topic createTopic(String title, int position, Topic.TopicType type, Document document) {
        Topic topic = new Topic();
        topic.setTitle(title);
        topic.setStartPosition(position);
        topic.setType(type);
        topic.setDocument(document);
        topic.setSummaryGenerationStatus(Topic.SummaryGenerationStatus.PENDING);
        
        return topicRepository.save(topic);
    }
    
    private Topic findAppropriateParent(List<Topic> topics, Topic newTopic) {
        if (topics.isEmpty()) {
            return null;
        }
        
        for (int i = topics.size() - 1; i >= 0; i--) {
            Topic potentialParent = topics.get(i);
            if (isAppropriateParent(potentialParent, newTopic)) {
                return potentialParent;
            }
        }
        
        return null;
    }
    
    private boolean isAppropriateParent(Topic potentialParent, Topic child) {
        if (potentialParent.getType() == Topic.TopicType.HEADING && 
            child.getType() == Topic.TopicType.SUBHEADING) {
            return true;
        }
        
        if (potentialParent.getType() == Topic.TopicType.SUBHEADING && 
            child.getType() == Topic.TopicType.SUBHEADING && 
            potentialParent.getLevel() < child.getLevel()) {
            return true;
        }
        
        return false;
    }
    
    private String extractTopicContext(List<String> paragraphs, int topicIndex) {
        StringBuilder context = new StringBuilder();
        
        String topicTitle = paragraphs.get(topicIndex).split("\\n")[0].trim();
        
        String numericId = "";
        Pattern numPattern = Pattern.compile("^\\s*(\\d+(?:\\.\\d+)*)\\s+.*$");
        Matcher numMatcher = numPattern.matcher(topicTitle);
        if (numMatcher.matches()) {
            numericId = numMatcher.group(1);
        }
        
        context.append(paragraphs.get(topicIndex)).append("\n\n");
        
        int i = topicIndex + 1;
        while (i < paragraphs.size()) {
            String paragraph = paragraphs.get(i);
            String firstLine = paragraph.split("\\n")[0].trim();
            
            boolean isNewTopic = false;
            
            for (Pattern pattern : SECTION_BOUNDARY_PATTERNS) {
                if (pattern.matcher(firstLine).matches()) {
                    isNewTopic = true;
                    break;
                }
            }
            
            if (!isNewTopic && (isAllCaps(firstLine) || isTitleCase(firstLine))) {
                if (!firstLine.endsWith(".") || firstLine.split("\\s+").length <= 5) {
                    isNewTopic = true;
                }
            }
            
            if (!isNewTopic) {
                Pattern numberPattern = Pattern.compile("^\\s*(\\d+(?:\\.\\d+)*)\\s+(.+)$");
                Matcher numberMatcher = numberPattern.matcher(firstLine);
                
                if (numberMatcher.matches()) {
                    if (!numericId.isEmpty()) {
                        String newNumericId = numberMatcher.group(1);
                        if (!newNumericId.startsWith(numericId + ".")) {
                            isNewTopic = true;
                        }
                    } else {
                        isNewTopic = true;
                    }
                }
            }
            
            if (isNewTopic) {
                break;
            }
            
            context.append(paragraph).append("\n\n");
            
            if (context.length() > maxContentLength) {
                context.setLength(maxContentLength);
                break;
            }
            
            i++;
        }
        
        if (context.length() < 500 && !numericId.isEmpty()) {
            log.info("Using alternative content extraction for topic with numeric ID: {}", numericId);
            
            Pattern contentPattern = Pattern.compile("(?s)" + Pattern.quote(numericId) + "\\s+.*?(?=(\\d+(?:\\.\\d+)*\\s+|$))");
            Matcher contentMatcher = contentPattern.matcher(String.join("\n\n", paragraphs));
            
            if (contentMatcher.find()) {
                String extractedContent = contentMatcher.group(0);
                if (extractedContent.length() > context.length()) {
                    context = new StringBuilder(extractedContent);
                }
            }
            
            if (context.length() < 500) {
                log.info("Using position-based content extraction for topic with numeric ID: {}", numericId);
                
                int maxParagraphsToInclude = 5;
                int paragraphsIncluded = 0;
                
                context = new StringBuilder(paragraphs.get(topicIndex)).append("\n\n");
                
                i = topicIndex + 1;
                while (i < paragraphs.size() && paragraphsIncluded < maxParagraphsToInclude) {
                    context.append(paragraphs.get(i)).append("\n\n");
                    paragraphsIncluded++;
                    
                    if (context.length() > maxContentLength) {
                        context.setLength(maxContentLength);
                        break;
                    }
                    
                    i++;
                }
            }
        }
        
        return context.toString().trim();
    }
    
    private List<String> extractKeywords(String text) {
        Map<String, Integer> wordFrequency = new HashMap<>();
        
        
        return new ArrayList<>();
    }
    
    private boolean isProbableSubheading(String line, String previousParagraph) {
        if (line.length() > 100) {
            return false;
        }
        
        if (line.endsWith(":")) {
            return true;
        }
        
        if (isTitleCase(line) || isAllCaps(line)) {
            return true;
        }
        
        String[] subheadingTerms = {"example", "note", "important", "warning", "tip", "summary"};
        for (String term : subheadingTerms) {
            if (line.toLowerCase().startsWith(term + ":") || 
                line.toLowerCase().startsWith(term + " -") ||
                line.toLowerCase().equals(term)) {
                return true;
            }
        }
        
        return false;
    }
    
    private boolean isAllCaps(String text) {
        return text.equals(text.toUpperCase()) && text.matches(".*[A-Z].*");
    }
    
    private boolean isTitleCase(String text) {
        String[] words = text.split("\\s+");
        if (words.length <= 1) {
            return false;
        }
        
        int capitalizedWords = 0;
        for (String word : words) {
            if (word.length() > 0 && Character.isUpperCase(word.charAt(0))) {
                capitalizedWords++;
            }
        }
        
        return (double) capitalizedWords / words.length > 0.7;
    }
    
    /**
     * Get all topics for a document extracted using NLP-based approach
     * @param documentId ID of the document
     * @return List of topics
     */
    public List<Topic> getTopicsByDocument(Long documentId) {
        
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Document not found with id: " + documentId));
        
        return topicRepository.findByDocumentId(document.getId());
    }
    
    /**
     * Compare extraction results between traditional and NLP-based approaches
     * @param documentId ID of the document
     * @return Comparison results
     */
    public Map<String, Object> compareExtractionResults(Long documentId) {
        
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Document not found with id: " + documentId));
        
        List<Topic> traditionalTopics = topicRepository.findByDocumentId(document.getId());
        
        List<Topic> nlpTopics = extractTopics(document);
        
        int traditionalTopicCount = traditionalTopics.size();
        int nlpTopicCount = nlpTopics.size();
        
        double traditionalAvgContentLength = traditionalTopics.stream()
                .mapToInt(t -> t.getContent() != null ? t.getContent().length() : 0)
                .average()
                .orElse(0);
        
        double nlpAvgContentLength = nlpTopics.stream()
                .mapToInt(t -> t.getContent() != null ? t.getContent().length() : 0)
                .average()
                .orElse(0);
        
        long traditionalNumericTitles = traditionalTopics.stream()
                .filter(t -> t.getTitle().matches("^\\d+.*$"))
                .count();
        
        long nlpNumericTitles = nlpTopics.stream()
                .filter(t -> t.getTitle().matches("^\\d+.*$"))
                .count();
        
        Map<String, Object> results = new HashMap<>();
        results.put("documentId", documentId);
        results.put("traditionalTopicCount", traditionalTopicCount);
        results.put("nlpTopicCount", nlpTopicCount);
        results.put("traditionalAvgContentLength", traditionalAvgContentLength);
        results.put("nlpAvgContentLength", nlpAvgContentLength);
        results.put("traditionalNumericTitles", traditionalNumericTitles);
        results.put("nlpNumericTitles", nlpNumericTitles);
        
        int sampleSize = Math.min(5, Math.min(traditionalTopicCount, nlpTopicCount));
        List<Map<String, Object>> sampleComparisons = new ArrayList<>();
        
        for (int i = 0; i < sampleSize; i++) {
            Map<String, Object> comparison = new HashMap<>();
            comparison.put("traditionalTitle", traditionalTopics.get(i).getTitle());
            comparison.put("nlpTitle", nlpTopics.get(i).getTitle());
            comparison.put("traditionalContentLength", traditionalTopics.get(i).getContent() != null ? 
                    traditionalTopics.get(i).getContent().length() : 0);
            comparison.put("nlpContentLength", nlpTopics.get(i).getContent() != null ? 
                    nlpTopics.get(i).getContent().length() : 0);
            sampleComparisons.add(comparison);
        }
        
        results.put("sampleComparisons", sampleComparisons);
        
        return results;
    }
}

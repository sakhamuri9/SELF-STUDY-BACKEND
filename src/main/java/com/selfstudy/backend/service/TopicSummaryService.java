package com.selfstudy.backend.service;

import com.selfstudy.backend.config.OpenAIConfig;
import com.selfstudy.backend.model.Topic;
import com.selfstudy.backend.model.TopicSummary;
import com.selfstudy.backend.repository.TopicRepository;
import com.selfstudy.backend.repository.TopicSummaryRepository;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.service.OpenAiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

@Service
@RequiredArgsConstructor
@Slf4j
public class TopicSummaryService {

    private final TopicRepository topicRepository;
    private final TopicSummaryRepository topicSummaryRepository;
    private final OpenAiService openAiService;
    private final OpenAIConfig.OpenAIRequestConfig openAIRequestConfig;

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
        
        String defaultContent = "Generating summary for " + topic.getTitle() + "...";
        summary.setContent(defaultContent);
        
        summary.setTopic(topic);
        summary.setType(type);
        summary.setStatus(TopicSummary.GenerationStatus.PROCESSING);
        
        summary = topicSummaryRepository.save(summary);
        
        try {
            String content;
            String examples;
            
            String prompt = buildPromptForSummary(topic, type);
            String openAiResponse = callOpenAI(prompt);
            
            String[] parts = parseOpenAiResponse(openAiResponse);
            content = parts[0];
            examples = parts[1];
            
            if (content == null || content.isEmpty()) {
                log.warn("OpenAI returned empty content for {} summary of topic {}. Using fallback.", type, topic.getId());
                switch (type) {
                    case BASIC:
                        content = generateSimpleBasicSummary(topic);
                        examples = generateSimpleBasicExamples(topic);
                        break;
                    case DETAILED:
                        content = generateSimpleDetailedSummary(topic);
                        examples = generateSimpleDetailedExamples(topic);
                        break;
                    case CHILD_FRIENDLY:
                        content = generateSimpleChildFriendlySummary(topic);
                        examples = generateSimpleChildFriendlyExamples(topic);
                        break;
                }
            }
            
            summary.setContent(content);
            summary.setExamples(examples);
            summary.setStatus(TopicSummary.GenerationStatus.COMPLETED);
            
        } catch (Exception e) {
            log.error("Error generating {} summary for topic {}: {}", type, topic.getId(), e.getMessage(), e);
            summary.setStatus(TopicSummary.GenerationStatus.FAILED);
            
            switch (type) {
                case BASIC:
                    summary.setContent(generateSimpleBasicSummary(topic));
                    summary.setExamples(generateSimpleBasicExamples(topic));
                    break;
                case DETAILED:
                    summary.setContent(generateSimpleDetailedSummary(topic));
                    summary.setExamples(generateSimpleDetailedExamples(topic));
                    break;
                case CHILD_FRIENDLY:
                    summary.setContent(generateSimpleChildFriendlySummary(topic));
                    summary.setExamples(generateSimpleChildFriendlyExamples(topic));
                    break;
            }
        }
        
        return topicSummaryRepository.save(summary);
    }
    
    private String buildPromptForSummary(Topic topic, TopicSummary.SummaryType type) {
        StringBuilder contextBuilder = new StringBuilder();
        
        contextBuilder.append("Topic Title: ").append(topic.getTitle()).append("\n\n");
        contextBuilder.append("Topic Content: ").append(topic.getContent()).append("\n\n");
        
        if (topic.getParent() != null) {
            contextBuilder.append("Parent Topic: ").append(topic.getParent().getTitle()).append("\n");
            contextBuilder.append("Parent Content: ").append(topic.getParent().getContent()).append("\n\n");
        }
        
        if (topic.getChildren() != null && !topic.getChildren().isEmpty()) {
            contextBuilder.append("Related Subtopics:\n");
            for (Topic child : topic.getChildren()) {
                contextBuilder.append("- ").append(child.getTitle()).append("\n");
            }
            contextBuilder.append("\n");
        }
        
        String context = contextBuilder.toString();
        String summaryType;
        String instructions;
        
        switch (type) {
            case BASIC:
                summaryType = "basic";
                instructions = "Create a concise, easy-to-understand summary that explains the key concepts in simple terms. " +
                        "Focus on the most important points and avoid technical jargon. " +
                        "The summary should be around 3-5 sentences.";
                break;
            case DETAILED:
                summaryType = "detailed";
                instructions = "Create a comprehensive summary that covers all the important aspects of the topic. " +
                        "Include technical details and explain concepts thoroughly. " +
                        "The summary should be around 8-10 sentences.";
                break;
            case CHILD_FRIENDLY:
                summaryType = "child-friendly";
                instructions = "Create a fun, engaging summary that explains the topic in a way that a 10-year-old would understand. " +
                        "Use simple language, analogies, and avoid technical terms. " +
                        "Make it entertaining and educational. " +
                        "The summary should be around 3-5 sentences.";
                break;
            default:
                summaryType = "basic";
                instructions = "Create a concise summary of the key points.";
        }
        
        String userPrompt = "Based on the following content from a textbook:\n\n" +
                context +
                "\n\nCreate a " + summaryType + " summary of this topic. " + instructions + "\n\n" +
                "Also provide 1-2 examples that illustrate the concept. " +
                "Format your response as follows:\n\n" +
                "SUMMARY:\n[Your summary here]\n\n" +
                "EXAMPLES:\n[Your examples here]";
        
        return userPrompt;
    }
    
    private String[] parseOpenAiResponse(String response) {
        String[] result = new String[2];
        String content = "";
        String examples = "";
        
        if (response != null && !response.isEmpty()) {
            String[] parts = response.split("(?i)EXAMPLES:");
            
            if (parts.length > 0) {
                String summaryPart = parts[0];
                int summaryIndex = summaryPart.indexOf("SUMMARY:");
                if (summaryIndex != -1) {
                    content = summaryPart.substring(summaryIndex + 8).trim();
                } else {
                    content = summaryPart.trim();
                }
                
                if (parts.length > 1) {
                    examples = parts[1].trim();
                }
            } else {
                content = response.trim();
            }
        }
        
        result[0] = content;
        result[1] = examples;
        return result;
    }
    
    private String callOpenAI(String prompt) {
        try {
            List<ChatMessage> messages = new ArrayList<>();
            
            ChatMessage systemMessage = new ChatMessage();
            systemMessage.setRole("system");
            systemMessage.setContent("You are an educational assistant that creates summaries of textbook content. " +
                    "Your task is to create summaries based ONLY on the provided content. " +
                    "If the content is insufficient to create a summary, say so clearly. " +
                    "Keep your summaries concise, relevant, and include examples when appropriate. " +
                    "Do not make up information that is not in the provided content.");
            messages.add(systemMessage);
            
            ChatMessage userMessage = new ChatMessage();
            userMessage.setRole("user");
            userMessage.setContent(prompt);
            messages.add(userMessage);
            
            ChatCompletionRequest completionRequest = ChatCompletionRequest.builder()
                    .model(openAIRequestConfig.getModel())
                    .messages(messages)
                    .maxTokens(openAIRequestConfig.getMaxTokens())
                    .temperature(openAIRequestConfig.getTemperature())
                    .build();
            
            log.info("Sending request to OpenAI for summary generation");
            String response = openAiService.createChatCompletion(completionRequest)
                    .getChoices().get(0).getMessage().getContent();
            log.info("Received response from OpenAI: {}", response.substring(0, Math.min(100, response.length())) + "...");
            
            return response;
        } catch (Exception e) {
            log.error("Error calling OpenAI: {}", e.getMessage(), e);
            if (e instanceof TimeoutException) {
                throw new RuntimeException("OpenAI request timed out", e);
            }
            throw new RuntimeException("Error calling OpenAI", e);
        }
    }
    
    private String generateSimpleBasicSummary(Topic topic) {
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
    
    private String generateSimpleDetailedSummary(Topic topic) {
        String content = topic.getContent();
        if (content == null || content.isEmpty()) {
            return "No detailed content available for this topic.";
        }
        
        return "This topic covers " + topic.getTitle() + ". " + content;
    }
    
    private String generateSimpleChildFriendlySummary(Topic topic) {
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
    
    private String generateSimpleBasicExamples(Topic topic) {
        return "Example: Consider how " + topic.getTitle() + " is used in practice.";
    }
    
    private String generateSimpleDetailedExamples(Topic topic) {
        return "Example 1: " + topic.getTitle() + " can be applied in various scenarios.\n\n" +
               "Example 2: Here's how professionals use " + topic.getTitle() + " in their work.";
    }
    
    private String generateSimpleChildFriendlyExamples(Topic topic) {
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

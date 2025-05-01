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
            String content = defaultContent;
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
        
        try {
            log.info("Generating basic summary for topic: {}", topic.getId());
            
            List<ChatMessage> messages = new ArrayList<>();
            messages.add(new ChatMessage("system", 
                "You are an educational content summarizer. Create a concise, basic summary of the following topic. " +
                "Focus on the key points and main ideas. Keep it clear and straightforward, suitable for a quick overview. " +
                "The summary should be 2-3 sentences long."));
            
            messages.add(new ChatMessage("user", 
                "Topic title: " + topic.getTitle() + "\n\n" +
                "Topic content: " + content));
            
            ChatCompletionRequest request = ChatCompletionRequest.builder()
                .model(openAIRequestConfig.getModel())
                .messages(messages)
                .temperature(0.7)
                .maxTokens(150)
                .build();
            
            String summary = openAiService.createChatCompletion(request)
                .getChoices().get(0).getMessage().getContent();
            
            log.info("Successfully generated basic summary for topic: {}", topic.getId());
            return summary;
            
        } catch (Exception e) {
            log.error("Error generating basic summary with OpenAI: {}", e.getMessage(), e);
            
            String[] sentences = content.split("[.!?]\\s+");
            if (sentences.length <= 2) {
                return content;
            }
            return sentences[0] + ". " + sentences[1] + ".";
        }
    }
    
    private String generateDetailedSummary(Topic topic) {
        String content = topic.getContent();
        if (content == null || content.isEmpty()) {
            return "No detailed content available for this topic.";
        }
        
        try {
            log.info("Generating detailed summary for topic: {}", topic.getId());
            
            List<ChatMessage> messages = new ArrayList<>();
            messages.add(new ChatMessage("system", 
                "You are an educational content expert. Create a comprehensive, detailed summary of the following topic. " +
                "Include important concepts, relationships, and implications. The summary should be thorough and educational, " +
                "suitable for someone who wants to understand the topic in depth. Include technical details where appropriate. " +
                "The summary should be 4-6 sentences long."));
            
            messages.add(new ChatMessage("user", 
                "Topic title: " + topic.getTitle() + "\n\n" +
                "Topic content: " + content));
            
            ChatCompletionRequest request = ChatCompletionRequest.builder()
                .model(openAIRequestConfig.getModel())
                .messages(messages)
                .temperature(0.7)
                .maxTokens(300)
                .build();
            
            String summary = openAiService.createChatCompletion(request)
                .getChoices().get(0).getMessage().getContent();
            
            log.info("Successfully generated detailed summary for topic: {}", topic.getId());
            return summary;
            
        } catch (Exception e) {
            log.error("Error generating detailed summary with OpenAI: {}", e.getMessage(), e);
            
            return "This topic covers " + topic.getTitle() + ". " + content;
        }
    }
    
    private String generateChildFriendlySummary(Topic topic) {
        String content = topic.getContent();
        if (content == null || content.isEmpty()) {
            return "Nothing to read here yet!";
        }
        
        try {
            log.info("Generating child-friendly summary for topic: {}", topic.getId());
            
            List<ChatMessage> messages = new ArrayList<>();
            messages.add(new ChatMessage("system", 
                "You are an educational content creator for children. Create a fun, engaging, and simple summary of the following topic. " +
                "Use simple language, analogies, and a friendly tone. Avoid complex terminology. Make it exciting and easy to understand " +
                "for a child aged 8-12. The summary should be 3-4 sentences long."));
            
            messages.add(new ChatMessage("user", 
                "Topic title: " + topic.getTitle() + "\n\n" +
                "Topic content: " + content));
            
            ChatCompletionRequest request = ChatCompletionRequest.builder()
                .model(openAIRequestConfig.getModel())
                .messages(messages)
                .temperature(0.8)
                .maxTokens(200)
                .build();
            
            String summary = openAiService.createChatCompletion(request)
                .getChoices().get(0).getMessage().getContent();
            
            log.info("Successfully generated child-friendly summary for topic: {}", topic.getId());
            return summary;
            
        } catch (Exception e) {
            log.error("Error generating child-friendly summary with OpenAI: {}", e.getMessage(), e);
            
            String[] sentences = content.split("[.!?]\\s+");
            if (sentences.length == 0) {
                return "This is about " + topic.getTitle() + "!";
            }
            String firstSentence = sentences[0].replaceAll("\\b\\w{10,}\\b", "thing");
            return "Let's learn about " + topic.getTitle() + "! " + firstSentence + ".";
        }
    }
    
    private String generateBasicExamples(Topic topic) {
        try {
            log.info("Generating basic examples for topic: {}", topic.getId());
            
            List<ChatMessage> messages = new ArrayList<>();
            messages.add(new ChatMessage("system", 
                "You are an educational content creator. Create 1-2 practical, real-world examples that illustrate the concept. " +
                "The examples should be clear, concise, and help reinforce understanding of the topic. " +
                "Keep the examples straightforward and focused on practical application."));
            
            messages.add(new ChatMessage("user", 
                "Topic title: " + topic.getTitle() + "\n\n" +
                "Topic content: " + topic.getContent()));
            
            ChatCompletionRequest request = ChatCompletionRequest.builder()
                .model(openAIRequestConfig.getModel())
                .messages(messages)
                .temperature(0.7)
                .maxTokens(150)
                .build();
            
            String examples = openAiService.createChatCompletion(request)
                .getChoices().get(0).getMessage().getContent();
            
            log.info("Successfully generated basic examples for topic: {}", topic.getId());
            return examples;
            
        } catch (Exception e) {
            log.error("Error generating basic examples with OpenAI: {}", e.getMessage(), e);
            
            return "Example: Consider how " + topic.getTitle() + " is used in practice.";
        }
    }
    
    private String generateDetailedExamples(Topic topic) {
        try {
            log.info("Generating detailed examples for topic: {}", topic.getId());
            
            List<ChatMessage> messages = new ArrayList<>();
            messages.add(new ChatMessage("system", 
                "You are an educational content expert. Create 2-3 detailed, technical examples that demonstrate the concept in depth. " +
                "The examples should showcase different aspects or applications of the topic. Include technical details, " +
                "potential challenges, and best practices where appropriate. Format as 'Example 1:', 'Example 2:', etc."));
            
            messages.add(new ChatMessage("user", 
                "Topic title: " + topic.getTitle() + "\n\n" +
                "Topic content: " + topic.getContent()));
            
            ChatCompletionRequest request = ChatCompletionRequest.builder()
                .model(openAIRequestConfig.getModel())
                .messages(messages)
                .temperature(0.7)
                .maxTokens(300)
                .build();
            
            String examples = openAiService.createChatCompletion(request)
                .getChoices().get(0).getMessage().getContent();
            
            log.info("Successfully generated detailed examples for topic: {}", topic.getId());
            return examples;
            
        } catch (Exception e) {
            log.error("Error generating detailed examples with OpenAI: {}", e.getMessage(), e);
            
            return "Example 1: " + topic.getTitle() + " can be applied in various scenarios.\n\n" +
                   "Example 2: Here's how professionals use " + topic.getTitle() + " in their work.";
        }
    }
    
    private String generateChildFriendlyExamples(Topic topic) {
        try {
            log.info("Generating child-friendly examples for topic: {}", topic.getId());
            
            List<ChatMessage> messages = new ArrayList<>();
            messages.add(new ChatMessage("system", 
                "You are creating educational content for children. Create 1-2 fun, engaging examples that illustrate the concept " +
                "in a way that children aged 8-12 can understand and relate to. Use simple language, familiar scenarios, " +
                "and possibly elements of play or imagination. Make it exciting and memorable!"));
            
            messages.add(new ChatMessage("user", 
                "Topic title: " + topic.getTitle() + "\n\n" +
                "Topic content: " + topic.getContent()));
            
            ChatCompletionRequest request = ChatCompletionRequest.builder()
                .model(openAIRequestConfig.getModel())
                .messages(messages)
                .temperature(0.8)
                .maxTokens(200)
                .build();
            
            String examples = openAiService.createChatCompletion(request)
                .getChoices().get(0).getMessage().getContent();
            
            log.info("Successfully generated child-friendly examples for topic: {}", topic.getId());
            return examples;
            
        } catch (Exception e) {
            log.error("Error generating child-friendly examples with OpenAI: {}", e.getMessage(), e);
            
            return "Fun example: Imagine you're playing with " + topic.getTitle() + "!";
        }
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

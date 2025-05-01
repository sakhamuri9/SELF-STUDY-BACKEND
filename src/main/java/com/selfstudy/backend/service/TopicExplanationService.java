package com.selfstudy.backend.service;

import com.selfstudy.backend.config.OpenAIConfig;
import com.selfstudy.backend.dto.TopicExplanationRequest;
import com.selfstudy.backend.model.Topic;
import com.selfstudy.backend.repository.TopicRepository;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.service.OpenAiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;

@Service
@RequiredArgsConstructor
@Slf4j
public class TopicExplanationService {

    private final OpenAiService openAiService;
    private final TopicRepository topicRepository;
    private final OpenAIConfig.OpenAIRequestConfig openAIRequestConfig;

    public String generateExplanation(Long topicId, TopicExplanationRequest request) {
        log.info("Generating explanation for topic: {} with question: {}", topicId, request.getQuestion());
        
        Topic topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new RuntimeException("Topic not found with id: " + topicId));
        
        try {
            String prompt = buildPrompt(topic, request);
            return callOpenAI(prompt);
        } catch (Exception e) {
            log.error("Error generating explanation: {}", e.getMessage(), e);
            if (e instanceof TimeoutException) {
                return "Sorry, the explanation generation timed out. Please try again with a simpler question.";
            }
            return "Sorry, there was an error generating the explanation. Please try again later.";
        }
    }
    
    private String buildPrompt(Topic topic, TopicExplanationRequest request) {
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
        
        String systemPrompt = "You are an educational assistant that explains topics from a textbook. " +
                "Your task is to answer questions based ONLY on the provided content. " +
                "If the answer cannot be derived from the provided content, say so clearly. " +
                "Keep your answers concise, relevant, and include examples when appropriate. " +
                "Do not make up information that is not in the provided content.";
        
        String userPrompt = "Based on the following content from a textbook:\n\n" +
                context +
                "\n\nQuestion: " + request.getQuestion() + "\n\n" +
                "Please provide a concise explanation with examples if applicable. " +
                (request.getPreferredStyle() != null ? "Use a " + request.getPreferredStyle() + " style." : "");
        
        return userPrompt;
    }
    
    private String callOpenAI(String prompt) {
        List<ChatMessage> messages = new ArrayList<>();
        
        ChatMessage systemMessage = new ChatMessage();
        systemMessage.setRole("system");
        systemMessage.setContent("You are an educational assistant that explains topics from a textbook. " +
                "Your task is to answer questions based ONLY on the provided content. " +
                "If the answer cannot be derived from the provided content, say so clearly. " +
                "Keep your answers concise, relevant, and include examples when appropriate. " +
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
        
        return openAiService.createChatCompletion(completionRequest)
                .getChoices().get(0).getMessage().getContent();
    }
}

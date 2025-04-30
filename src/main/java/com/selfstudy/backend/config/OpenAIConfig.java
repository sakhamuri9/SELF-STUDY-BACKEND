package com.selfstudy.backend.config;

import com.theokanning.openai.service.OpenAiService;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class OpenAIConfig {

    @Value("${openai.api.key}")
    private String openaiApiKey;

    @Value("${openai.api.timeout:10}")
    private int timeout;

    @Value("${openai.api.model:gpt-3.5-turbo}")
    private String model;

    @Value("${openai.api.max-tokens:500}")
    private int maxTokens;

    @Value("${openai.api.temperature:0.7}")
    private double temperature;

    @Bean
    public OpenAiService openAiService() {
        return new OpenAiService(openaiApiKey, Duration.ofSeconds(timeout));
    }

    @Getter
    @Setter
    public static class OpenAIRequestConfig {
        private String model;
        private int maxTokens;
        private double temperature;
        
        public static OpenAIRequestConfig defaultConfig(String model, int maxTokens, double temperature) {
            OpenAIRequestConfig config = new OpenAIRequestConfig();
            config.setModel(model);
            config.setMaxTokens(maxTokens);
            config.setTemperature(temperature);
            return config;
        }
    }
    
    @Bean
    public OpenAIRequestConfig openAIRequestConfig() {
        return OpenAIRequestConfig.defaultConfig(model, maxTokens, temperature);
    }
}

package com.selfstudy.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Configuration for topic extraction services
 */
@Configuration
public class TopicExtractionConfig {

    @Value("${topic.extraction.use-nlp:false}")
    private boolean useNlpExtraction;
    
    @Value("${topic.extraction.max-content-length:5000}")
    private int maxContentLength;
    
    @Value("${topic.extraction.min-paragraph-length:50}")
    private int minParagraphLength;
    
    @Bean
    public boolean useNlpExtraction() {
        return useNlpExtraction;
    }
    
    @Bean
    public int maxContentLength() {
        return maxContentLength;
    }
    
    @Bean
    public int minParagraphLength() {
        return minParagraphLength;
    }
}

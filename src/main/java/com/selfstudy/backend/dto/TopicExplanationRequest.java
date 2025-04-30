package com.selfstudy.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TopicExplanationRequest {
    
    @NotBlank(message = "Question is required")
    @Size(min = 5, max = 500, message = "Question must be between 5 and 500 characters")
    private String question;
    
    private String preferredStyle;
}

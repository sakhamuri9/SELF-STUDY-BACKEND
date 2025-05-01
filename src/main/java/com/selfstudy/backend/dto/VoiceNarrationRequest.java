package com.selfstudy.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VoiceNarrationRequest {
    
    private String voiceId;
    
    private String engine;
    
    private String languageCode;
}

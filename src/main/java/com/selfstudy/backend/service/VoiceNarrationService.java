package com.selfstudy.backend.service;

import com.amazonaws.services.polly.AmazonPolly;
import com.amazonaws.services.polly.model.DescribeVoicesRequest;
import com.amazonaws.services.polly.model.DescribeVoicesResult;
import com.amazonaws.services.polly.model.OutputFormat;
import com.amazonaws.services.polly.model.SynthesizeSpeechRequest;
import com.amazonaws.services.polly.model.SynthesizeSpeechResult;
import com.amazonaws.services.polly.model.TextType;
import com.amazonaws.services.polly.model.Voice;
import com.amazonaws.services.polly.model.VoiceId;
import com.selfstudy.backend.exception.ResourceNotFoundException;
import com.selfstudy.backend.model.AudioFile;
import com.selfstudy.backend.model.AudioFile.AudioType;
import com.selfstudy.backend.model.Topic;
import com.selfstudy.backend.model.TopicSummary;
import com.selfstudy.backend.repository.AudioFileRepository;
import com.selfstudy.backend.repository.TopicRepository;
import com.selfstudy.backend.repository.TopicSummaryRepository;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class VoiceNarrationService {

    private final AmazonPolly amazonPolly;
    private final AudioFileRepository audioFileRepository;
    private final TopicRepository topicRepository;
    private final TopicSummaryRepository topicSummaryRepository;
    
    @Value("${voice.narration.storage-dir:audio-files}")
    @Getter
    private String audioStorageDir;
    
    @Value("${voice.narration.default-voice:Joanna}")
    private String defaultVoice;
    
    /**
     * Initialize storage directory on bean creation
     */
    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(Paths.get(audioStorageDir));
            log.info("Created audio storage directory: {}", audioStorageDir);
        } catch (IOException e) {
            throw new RuntimeException("Could not initialize audio storage directory", e);
        }
    }
    
    /**
     * Generate or retrieve audio for a topic's content
     */
    @Transactional
    @Cacheable(value = "audioFiles", key = "'topic_' + #topicId + '_' + #voiceId")
    public String generateTopicAudio(Long topicId, String voiceId) {
        log.info("Generating audio for topic ID: {} with voice: {}", topicId, voiceId);
        
        Topic topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new ResourceNotFoundException("Topic not found with id: " + topicId));
        
        Optional<AudioFile> existingAudio = audioFileRepository.findByTopicIdAndAudioTypeAndVoiceId(
                topicId, AudioType.TOPIC_CONTENT, voiceId);
        
        if (existingAudio.isPresent()) {
            AudioFile audioFile = existingAudio.get();
            audioFile.recordAccess();
            audioFileRepository.save(audioFile);
            return audioFile.getFilePath();
        }
        
        return generateAndSaveAudio(topic.getContent(), voiceId, topic, null);
    }
    
    /**
     * Generate or retrieve audio for a summary's content
     */
    @Transactional
    @Cacheable(value = "audioFiles", key = "'summary_' + #summaryId + '_' + #voiceId")
    public String generateSummaryAudio(Long summaryId, String voiceId) {
        log.info("Generating audio for summary ID: {} with voice: {}", summaryId, voiceId);
        
        TopicSummary summary = topicSummaryRepository.findById(summaryId)
                .orElseThrow(() -> new ResourceNotFoundException("Summary not found with id: " + summaryId));
        
        Optional<AudioFile> existingAudio = audioFileRepository.findBySummaryIdAndVoiceId(summaryId, voiceId);
        
        if (existingAudio.isPresent()) {
            AudioFile audioFile = existingAudio.get();
            audioFile.recordAccess();
            audioFileRepository.save(audioFile);
            return audioFile.getFilePath();
        }
        
        return generateAndSaveAudio(summary.getContent(), voiceId, null, summary);
    }
    
    /**
     * Load audio file as a resource
     */
    public Resource loadAudioAsResource(String filePath) {
        try {
            Path path = Paths.get(filePath);
            Resource resource = new UrlResource(path.toUri());
            
            if (resource.exists() && resource.isReadable()) {
                return resource;
            } else {
                throw new RuntimeException("Could not read audio file: " + filePath);
            }
        } catch (Exception e) {
            throw new RuntimeException("Error loading audio file: " + filePath, e);
        }
    }
    
    /**
     * Get available voices from Amazon Polly
     */
    @Cacheable(value = "voices")
    public Map<String, Object> getAvailableVoices() {
        log.info("Fetching available voices from Amazon Polly");
        
        try {
            DescribeVoicesRequest describeVoicesRequest = new DescribeVoicesRequest();
            DescribeVoicesResult describeVoicesResult = amazonPolly.describeVoices(describeVoicesRequest);
            List<Voice> voices = describeVoicesResult.getVoices();
            
            Map<String, Object> result = new HashMap<>();
            result.put("defaultVoice", defaultVoice);
            result.put("voices", voices.stream()
                    .map(voice -> {
                        Map<String, Object> voiceMap = new HashMap<>();
                        voiceMap.put("id", voice.getId());
                        voiceMap.put("name", voice.getName());
                        voiceMap.put("gender", voice.getGender());
                        voiceMap.put("languageCode", voice.getLanguageCode());
                        voiceMap.put("languageName", voice.getLanguageName());
                        return voiceMap;
                    })
                    .collect(Collectors.toList()));
            
            return result;
        } catch (Exception e) {
            log.error("Error fetching available voices: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to fetch available voices", e);
        }
    }
    
    /**
     * Generate audio using Amazon Polly and save to file system
     */
    private String generateAndSaveAudio(String text, String voiceId, Topic topic, TopicSummary summary) {
        try {
            init();
            
            String fileName = UUID.randomUUID().toString() + ".mp3";
            String filePath = Paths.get(audioStorageDir, fileName).toString();
            
            SynthesizeSpeechRequest synthesizeSpeechRequest = new SynthesizeSpeechRequest()
                    .withOutputFormat(OutputFormat.Mp3)
                    .withText(text)
                    .withTextType(TextType.Text)
                    .withVoiceId(voiceId != null ? VoiceId.fromValue(voiceId) : VoiceId.fromValue(defaultVoice));
            
            SynthesizeSpeechResult synthesizeSpeechResult = amazonPolly.synthesizeSpeech(synthesizeSpeechRequest);
            
            try (InputStream inputStream = synthesizeSpeechResult.getAudioStream();
                 FileOutputStream outputStream = new FileOutputStream(new File(filePath))) {
                
                byte[] buffer = new byte[4096];
                int bytesRead;
                long fileSize = 0;
                
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                    fileSize += bytesRead;
                }
                
                AudioFile audioFile = AudioFile.builder()
                        .fileName(fileName)
                        .filePath(filePath)
                        .contentType("audio/mpeg")
                        .fileSize(fileSize)
                        .audioType(topic != null ? AudioType.TOPIC_CONTENT : AudioType.SUMMARY_CONTENT)
                        .voiceId(voiceId != null ? voiceId : defaultVoice)
                        .topic(topic)
                        .summary(summary)
                        .createdAt(LocalDateTime.now())
                        .lastAccessedAt(LocalDateTime.now())
                        .accessCount(1)
                        .build();
                
                audioFileRepository.save(audioFile);
                
                log.info("Generated audio file: {} with size: {} bytes", filePath, fileSize);
                return filePath;
            }
        } catch (Exception e) {
            log.error("Error generating audio: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate audio", e);
        }
    }
}

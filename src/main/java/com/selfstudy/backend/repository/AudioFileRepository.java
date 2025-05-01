package com.selfstudy.backend.repository;

import com.selfstudy.backend.model.AudioFile;
import com.selfstudy.backend.model.AudioFile.AudioType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AudioFileRepository extends JpaRepository<AudioFile, Long> {
    
    Optional<AudioFile> findByTopicIdAndAudioTypeAndVoiceId(Long topicId, AudioType audioType, String voiceId);
    
    Optional<AudioFile> findBySummaryIdAndVoiceId(Long summaryId, String voiceId);
    
    List<AudioFile> findByTopicId(Long topicId);
    
    List<AudioFile> findBySummaryId(Long summaryId);
}

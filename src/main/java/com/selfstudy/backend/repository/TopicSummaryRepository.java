package com.selfstudy.backend.repository;

import com.selfstudy.backend.model.TopicSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TopicSummaryRepository extends JpaRepository<TopicSummary, Long> {
    
    List<TopicSummary> findByTopicId(Long topicId);
    
    Optional<TopicSummary> findByTopicIdAndType(Long topicId, TopicSummary.SummaryType type);
    
    List<TopicSummary> findByStatus(TopicSummary.GenerationStatus status);
}

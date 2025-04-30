package com.selfstudy.backend.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "topic_summaries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class TopicSummary extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "topic_id", nullable = false)
    @NotNull(message = "Topic is required")
    @JsonBackReference(value = "topic-summaries")
    private Topic topic;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SummaryType type;

    @Lob
    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String examples;

    @Column(name = "generation_status")
    @Enumerated(EnumType.STRING)
    private GenerationStatus status = GenerationStatus.PENDING;

    public enum SummaryType {
        BASIC,
        DETAILED,
        CHILD_FRIENDLY
    }

    public enum GenerationStatus {
        PENDING,
        PROCESSING,
        COMPLETED,
        FAILED
    }
}

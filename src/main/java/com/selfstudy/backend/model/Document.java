package com.selfstudy.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Lob;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "documents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Document extends BaseEntity {

    @NotBlank(message = "Title is required")
    @Size(max = 255, message = "Title must be less than 255 characters")
    @Column(nullable = false)
    private String title;

    @Size(max = 1000, message = "Description must be less than 1000 characters")
    @Column(length = 1000)
    private String description;

    @NotBlank(message = "File path is required")
    @Column(nullable = false)
    private String filePath;

    @Column(nullable = false)
    private String fileType;

    @Column(nullable = false)
    private Long fileSize;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String extractedText;

    @Column(name = "page_count")
    private Integer pageCount;

    @Column(name = "extraction_progress")
    private Integer extractionProgress;

    @Column(name = "topic_extraction_status")
    @Enumerated(EnumType.STRING)
    private TopicExtractionStatus topicExtractionStatus = TopicExtractionStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProcessingStatus status;

    @OneToMany(mappedBy = "document", fetch = FetchType.LAZY)
    private List<Topic> topics = new ArrayList<>();

    public enum ProcessingStatus {
        PENDING,
        PROCESSING,
        COMPLETED,
        FAILED
    }
    
    public enum TopicExtractionStatus {
        PENDING,
        PROCESSING,
        COMPLETED,
        FAILED
    }
}

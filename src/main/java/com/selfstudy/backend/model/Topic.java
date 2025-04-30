package com.selfstudy.backend.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "topics")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
public class Topic extends BaseEntity {

    @NotBlank(message = "Title is required")
    @Column(nullable = false)
    private String title;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(name = "start_position")
    private Integer startPosition;

    @Column(name = "end_position")
    private Integer endPosition;

    @Column(name = "level")
    private Integer level;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TopicType type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    @NotNull(message = "Document is required")
    @JsonBackReference(value = "document-topics")
    private Document document;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    @JsonBackReference(value = "topic-children")
    private Topic parent;
    
    @OneToMany(mappedBy = "parent", fetch = FetchType.LAZY)
    @JsonManagedReference(value = "topic-children")
    private List<Topic> children = new ArrayList<>();
    
    @OneToMany(mappedBy = "topic", fetch = FetchType.LAZY)
    @JsonManagedReference(value = "topic-summaries")
    private List<TopicSummary> summaries = new ArrayList<>();

    @Column(name = "summary_generation_status")
    @Enumerated(EnumType.STRING)
    private SummaryGenerationStatus summaryGenerationStatus = SummaryGenerationStatus.PENDING;

    public enum TopicType {
        CHAPTER,
        HEADING,
        SUBHEADING,
        PARAGRAPH
    }
    
    public enum SummaryGenerationStatus {
        PENDING,
        PROCESSING,
        COMPLETED,
        FAILED
    }
}

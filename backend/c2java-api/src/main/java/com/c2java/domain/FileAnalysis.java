package com.c2java.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 파일 분석 결과 엔티티
 */
@Entity
@Table(name = "file_analysis")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id")
    private ConversionJob job;

    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    @Column(name = "file_type")
    private String fileType;

    @Column(name = "line_count")
    private Integer lineCount;

    @Column(name = "function_count")
    private Integer functionCount;

    @Column(name = "struct_count")
    private Integer structCount;

    @Column(name = "include_count")
    private Integer includeCount;

    @Column(name = "complexity_score")
    private Double complexityScore;

    @Column(name = "analysis_result", columnDefinition = "TEXT")
    private String analysisResult;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}

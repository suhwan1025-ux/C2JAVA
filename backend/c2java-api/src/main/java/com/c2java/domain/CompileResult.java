package com.c2java.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 컴파일 결과 엔티티
 */
@Entity
@Table(name = "compile_results")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompileResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id")
    private ConversionJob job;

    @Column(name = "attempt_number", nullable = false)
    @Builder.Default
    private Integer attemptNumber = 1;

    @Column(nullable = false)
    private Boolean success;

    @Column(name = "error_output", columnDefinition = "TEXT")
    private String errorOutput;

    @Column(name = "warning_count")
    @Builder.Default
    private Integer warningCount = 0;

    @Column(name = "error_count")
    @Builder.Default
    private Integer errorCount = 0;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}

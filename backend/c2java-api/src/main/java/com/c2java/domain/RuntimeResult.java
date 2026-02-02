package com.c2java.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 런타임 테스트 결과 엔티티
 */
@Entity
@Table(name = "runtime_results")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuntimeResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id")
    private ConversionJob job;

    @Column(name = "test_name")
    private String testName;

    @Column(nullable = false)
    private Boolean success;

    @Column(name = "execution_time_ms")
    private Integer executionTimeMs;

    @Column(name = "error_output", columnDefinition = "TEXT")
    private String errorOutput;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}

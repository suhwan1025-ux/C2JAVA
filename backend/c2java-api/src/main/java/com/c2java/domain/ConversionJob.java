package com.c2java.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 변환 작업 엔티티
 * 멀티유저 지원
 */
@Entity
@Table(name = "conversion_jobs", indexes = {
    @Index(name = "idx_conversion_jobs_user_id", columnList = "user_id"),
    @Index(name = "idx_conversion_jobs_status", columnList = "status"),
    @Index(name = "idx_conversion_jobs_created_at", columnList = "created_at")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversionJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * 작업 소유자
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "job_name", nullable = false)
    private String jobName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private JobStatus status = JobStatus.PENDING;

    /**
     * 작업 우선순위 (높을수록 먼저 처리)
     */
    @Column(name = "priority")
    @Builder.Default
    private Integer priority = 0;

    @Column(name = "source_path")
    private String sourcePath;

    @Column(name = "output_path")
    private String outputPath;

    @Column(name = "llm_provider")
    private String llmProvider;

    /**
     * JDBC 설정 (JSON)
     */
    @Column(name = "jdbc_config", columnDefinition = "TEXT")
    private String jdbcConfig;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "compile_attempts")
    @Builder.Default
    private Integer compileAttempts = 0;

    /**
     * 작업 실행 서버 (분산 처리용)
     */
    @Column(name = "worker_id")
    private String workerId;

    /**
     * 작업 상태 열거형
     */
    public enum JobStatus {
        PENDING,      // 대기 중
        ANALYZING,    // 분석 중
        CONVERTING,   // 변환 중
        COMPILING,    // 컴파일 중
        TESTING,      // 테스트 중
        REVIEWING,    // 리뷰 생성 중
        COMPLETED,    // 완료
        FAILED        // 실패
    }

    /**
     * 작업 시작 처리
     */
    public void start() {
        this.status = JobStatus.ANALYZING;
    }

    /**
     * 작업 완료 처리
     */
    public void complete(String output) {
        this.completedAt = LocalDateTime.now();
        this.outputPath = output;
        this.status = JobStatus.COMPLETED;
    }

    /**
     * 작업 실패 처리
     */
    public void fail(String errorMessage) {
        this.completedAt = LocalDateTime.now();
        this.errorMessage = errorMessage;
        this.status = JobStatus.FAILED;
    }

    /**
     * 컴파일 시도 횟수 증가
     */
    public void incrementCompileAttempts() {
        this.compileAttempts++;
    }
}

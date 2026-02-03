package com.c2java.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 변환 작업 엔티티
 * C 파일 업로드부터 Java 생성, 컴파일, 테스트까지 전체 과정 추적
 */
@Entity
@Table(name = "conversion_jobs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversionJob {

    @Id
    @Column(name = "id")
    private UUID jobId;

    @Column(name = "job_name", nullable = false)
    private String jobName;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "target_language", nullable = false)
    private String targetLanguage; // springboot-3.2.5 등

    @Column(name = "llm_provider")
    private String llmProvider; // qwen3, gpt_oss

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private JobStatus status;

    @Column(name = "current_stage")
    private String currentStage; // UPLOAD, ANALYZE, CONVERT, COMPILE, TEST, COMPLETE

    @Column(name = "progress", nullable = false)
    private Integer progress; // 0-100

    // 원본 파일 정보
    @Column(name = "source_file_path")
    private String sourceFilePath;

    @Column(name = "source_path")
    private String sourcePath; // 기존 코드 호환

    @Column(name = "source_file_count")
    private Integer sourceFileCount;

    // 생성 파일 정보
    @Column(name = "generated_file_count")
    private Integer generatedFileCount;

    @Column(name = "output_path")
    private String outputPath;

    // Airflow 정보
    @Column(name = "airflow_dag_id")
    private String airflowDagId;

    @Column(name = "airflow_run_id")
    private String airflowRunId;

    // 분석 결과
    @Column(name = "function_count")
    private Integer functionCount;

    @Column(name = "struct_count")
    private Integer structCount;

    @Column(name = "sql_count")
    private Integer sqlCount;

    @Column(name = "review_required_count")
    private Integer reviewRequiredCount;

    // 검증 결과
    @Column(name = "compile_success")
    private Boolean compileSuccess;

    @Column(name = "compile_attempts")
    private Integer compileAttempts; // 컴파일 시도 횟수

    @Column(name = "compile_errors")
    @Lob
    private String compileErrors;

    @Column(name = "test_success")
    private Boolean testSuccess;

    @Column(name = "test_results")
    @Lob
    private String testResults;

    // 에러 정보
    @Column(name = "error_message")
    @Lob
    private String errorMessage;

    @Column(name = "error_stack_trace")
    @Lob
    private String errorStackTrace;

    // 실시간 로그
    @Column(name = "execution_log", columnDefinition = "TEXT")
    @Lob
    private String executionLog;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    public enum JobStatus {
        PENDING,      // 대기중
        ANALYZING,    // 분석중
        CONVERTING,   // 변환중
        COMPILING,    // 컴파일중
        TESTING,      // 테스트중
        REVIEWING,    // 리뷰중
        COMPLETED,    // 완료
        FAILED,       // 실패
        CANCELLED     // 취소
    }

    @PrePersist
    public void prePersist() {
        if (this.jobId == null) {
            this.jobId = UUID.randomUUID();
        }
        if (this.status == null) {
            this.status = JobStatus.PENDING;
        }
        if (this.progress == null) {
            this.progress = 0;
        }
        if (this.currentStage == null) {
            this.currentStage = "UPLOAD";
        }
        if (this.compileAttempts == null) {
            this.compileAttempts = 0;
        }
    }

    public void updateProgress(String stage, int progress) {
        this.currentStage = stage;
        this.progress = progress;
    }

    public void markCompleted() {
        this.status = JobStatus.COMPLETED;
        this.progress = 100;
        this.completedAt = LocalDateTime.now();
    }

    public void markFailed(String error) {
        this.status = JobStatus.FAILED;
        this.errorMessage = error;
    }

    // 기존 코드 호환성을 위한 메서드들
    public String getId() {
        return this.jobId != null ? this.jobId.toString() : null;
    }

    public String getSourcePath() {
        return this.sourceFilePath;
    }

    public void incrementCompileAttempts() {
        if (this.compileAttempts == null) {
            this.compileAttempts = 0;
        }
        this.compileAttempts++;
    }

    public void start() {
        this.status = JobStatus.ANALYZING;
        this.progress = 5;
    }

    public void fail(String error) {
        markFailed(error);
    }

    public void complete(String outputPath) {
        this.status = JobStatus.COMPLETED;
        this.outputPath = outputPath;
        this.progress = 100;
        this.completedAt = LocalDateTime.now();
    }
    
    /**
     * 실행 로그 추가
     */
    public void appendLog(String message) {
        String timestamp = LocalDateTime.now().toString();
        String logEntry = String.format("[%s] %s\n", timestamp, message);
        
        if (this.executionLog == null) {
            this.executionLog = logEntry;
        } else {
            this.executionLog += logEntry;
        }
    }
    
    /**
     * 로그 초기화
     */
    public void clearLog() {
        this.executionLog = "";
    }
}

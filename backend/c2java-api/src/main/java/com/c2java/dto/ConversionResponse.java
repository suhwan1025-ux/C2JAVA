package com.c2java.dto;

import com.c2java.domain.ConversionJob;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 변환 응답 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversionResponse {
    
    private String id;
    private String jobName;
    private String status;
    private String sourcePath;
    private String outputPath;
    private String llmProvider;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
    private String errorMessage;
    private Integer compileAttempts;
    
    /**
     * Entity에서 DTO 생성
     */
    public static ConversionResponse fromEntity(ConversionJob job) {
        return ConversionResponse.builder()
                .id(job.getId())
                .jobName(job.getJobName())
                .status(job.getStatus().name())
                .sourcePath(job.getSourcePath())
                .outputPath(job.getOutputPath())
                .llmProvider(job.getLlmProvider())
                .createdAt(job.getCreatedAt())
                .completedAt(job.getCompletedAt())
                .errorMessage(job.getErrorMessage())
                .compileAttempts(job.getCompileAttempts())
                .build();
    }
}

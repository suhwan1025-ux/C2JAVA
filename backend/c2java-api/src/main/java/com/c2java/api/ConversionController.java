package com.c2java.api;

import com.c2java.domain.ConversionJob.JobStatus;
import com.c2java.dto.ConversionRequest;
import com.c2java.dto.ConversionResponse;
import com.c2java.service.ConversionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * 변환 API 컨트롤러
 */
@RestController
@RequestMapping("/v1/conversions")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Conversion", description = "C to Java 변환 API")
public class ConversionController {

    private final ConversionService conversionService;

    /**
     * C 파일 업로드 및 변환 시작
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "변환 작업 생성", description = "C 파일을 업로드하고 Java로 변환을 시작합니다.")
    public ResponseEntity<ConversionResponse> createConversion(
            @Parameter(description = "변환할 C 소스 파일")
            @RequestPart("file") MultipartFile file,
            @Parameter(description = "작업 이름 (선택)")
            @RequestPart(value = "jobName", required = false) String jobName,
            @Parameter(description = "LLM 제공자 선택 (qwen3, gpt_oss)")
            @RequestPart(value = "llmProvider", required = false) String llmProvider,
            @Parameter(description = "JDBC URL (선택)")
            @RequestPart(value = "jdbcUrl", required = false) String jdbcUrl,
            @Parameter(description = "JDBC 사용자 (선택)")
            @RequestPart(value = "jdbcUser", required = false) String jdbcUser,
            @Parameter(description = "JDBC 비밀번호 (선택)")
            @RequestPart(value = "jdbcPassword", required = false) String jdbcPassword
    ) throws IOException {
        
        log.info("Received conversion request for file: {}", file.getOriginalFilename());
        
        ConversionRequest request = ConversionRequest.builder()
                .jobName(jobName)
                .llmProvider(llmProvider)
                .jdbcUrl(jdbcUrl)
                .jdbcUser(jdbcUser)
                .jdbcPassword(jdbcPassword)
                .build();
        
        ConversionResponse response = conversionService.createConversionJob(file, request);
        
        return ResponseEntity.ok(response);
    }

    /**
     * 작업 상태 조회
     */
    @GetMapping("/{jobId}")
    @Operation(summary = "작업 상태 조회", description = "특정 변환 작업의 현재 상태를 조회합니다.")
    public ResponseEntity<ConversionResponse> getJobStatus(
            @Parameter(description = "작업 ID")
            @PathVariable UUID jobId) {
        
        ConversionResponse response = conversionService.getJobStatus(jobId);
        return ResponseEntity.ok(response);
    }

    /**
     * 모든 작업 목록 조회
     */
    @GetMapping
    @Operation(summary = "전체 작업 목록", description = "모든 변환 작업 목록을 조회합니다.")
    public ResponseEntity<List<ConversionResponse>> getAllJobs() {
        List<ConversionResponse> jobs = conversionService.getAllJobs();
        return ResponseEntity.ok(jobs);
    }

    /**
     * 상태별 작업 목록 조회
     */
    @GetMapping("/status/{status}")
    @Operation(summary = "상태별 작업 목록", description = "특정 상태의 작업 목록을 조회합니다.")
    public ResponseEntity<List<ConversionResponse>> getJobsByStatus(
            @Parameter(description = "작업 상태")
            @PathVariable JobStatus status) {
        
        List<ConversionResponse> jobs = conversionService.getJobsByStatus(status);
        return ResponseEntity.ok(jobs);
    }
}

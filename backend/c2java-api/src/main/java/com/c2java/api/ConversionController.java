package com.c2java.api;

import com.c2java.domain.ConversionJob.JobStatus;
import com.c2java.dto.ConversionRequest;
import com.c2java.dto.ConversionResponse;
import com.c2java.service.ConversionService;
import com.c2java.service.ConversionPipelineService;
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
import java.util.Map;
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
    private final ConversionPipelineService pipelineService;

    /**
     * C 파일 업로드 및 변환 시작 (다중 파일 지원)
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "변환 작업 생성", description = "C 파일을 업로드하고 Java로 변환을 시작합니다.")
    public ResponseEntity<?> createConversion(
            @Parameter(description = "변환할 C 소스 파일들")
            @RequestPart(value = "files", required = false) List<MultipartFile> files,
            @Parameter(description = "단일 파일 (하위 호환성)")
            @RequestPart(value = "file", required = false) MultipartFile file,
            @Parameter(description = "작업 이름 (선택)")
            @RequestPart(value = "jobName", required = false) String jobName,
            @Parameter(description = "대상 언어")
            @RequestPart(value = "targetLanguage", required = false) String targetLanguage,
            @Parameter(description = "LLM 제공자 선택 (qwen3, gpt_oss)")
            @RequestPart(value = "llmProvider", required = false) String llmProvider,
            @Parameter(description = "JDBC 설정 (JSON)")
            @RequestPart(value = "jdbcConfig", required = false) String jdbcConfig
    ) {
        try {
            // 진행 중인 작업 확인
            List<ConversionResponse> ongoingJobs = conversionService.getAllJobs().stream()
                    .filter(job -> {
                        String status = job.getStatus();
                        return "PENDING".equals(status) || "ANALYZING".equals(status) || 
                               "CONVERTING".equals(status) || "COMPILING".equals(status) || 
                               "TESTING".equals(status);
                    })
                    .toList();
            
            if (!ongoingJobs.isEmpty()) {
                log.warn("Cannot start new conversion: {} ongoing jobs", ongoingJobs.size());
                return ResponseEntity.badRequest().body(Map.of(
                    "error", "ONGOING_JOBS_EXIST",
                    "message", "이미 진행 중인 변환 작업이 " + ongoingJobs.size() + "개 있습니다. 작업이 완료될 때까지 기다려주세요.",
                    "ongoingJobCount", ongoingJobs.size()
                ));
            }
            
            // 파일 목록 통합 (다중 파일 우선, 없으면 단일 파일)
            List<MultipartFile> uploadFiles = files != null && !files.isEmpty() ? files : 
                                             (file != null ? List.of(file) : List.of());
            
            if (uploadFiles.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "error", "NO_FILES",
                    "message", "업로드할 파일이 없습니다."
                ));
            }
            
            log.info("Received conversion request for {} files", uploadFiles.size());
            uploadFiles.forEach(f -> log.debug("  - {}", f.getOriginalFilename()));
            
            // 대상 언어 확인
            if (targetLanguage == null || targetLanguage.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "error", "NO_TARGET_LANGUAGE",
                    "message", "변환 대상 언어를 선택해주세요."
                ));
            }
            
            ConversionRequest request = ConversionRequest.builder()
                    .jobName(jobName != null ? jobName : uploadFiles.get(0).getOriginalFilename())
                    .llmProvider(llmProvider)
                    .targetLanguage(targetLanguage)
                    .build();
            
            // 다중 파일 변환 작업 생성
            ConversionResponse response = conversionService.createConversionJob(uploadFiles, request);
            
            log.info("Created conversion job: {} for {} files", response.getId(), uploadFiles.size());
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Failed to create conversion job", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "INTERNAL_ERROR",
                "message", "변환 작업 생성 중 오류가 발생했습니다: " + e.getMessage()
            ));
        }
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

    // ========== 파이프라인 API ==========

    /**
     * 분석 단계 실행 (Airflow Task에서 호출)
     */
    @PostMapping("/{jobId}/analyze")
    @Operation(summary = "파일 분석", description = "C 파일 구조를 분석합니다.")
    public ResponseEntity<Map<String, Object>> analyzeFiles(@PathVariable String jobId) {
        Map<String, Object> result = pipelineService.analyzeStep(jobId);
        return ResponseEntity.ok(result);
    }

    /**
     * 변환 단계 실행
     */
    @PostMapping("/{jobId}/convert")
    @Operation(summary = "코드 변환", description = "C 코드를 Java로 변환합니다.")
    public ResponseEntity<Map<String, Object>> convertCode(@PathVariable String jobId) {
        Map<String, Object> result = pipelineService.convertStep(jobId);
        return ResponseEntity.ok(result);
    }

    /**
     * 컴파일 단계 실행
     */
    @PostMapping("/{jobId}/compile")
    @Operation(summary = "컴파일 검증", description = "생성된 Java 코드를 컴파일합니다.")
    public ResponseEntity<Map<String, Object>> compileCode(@PathVariable String jobId) {
        Map<String, Object> result = pipelineService.compileStep(jobId);
        return ResponseEntity.ok(result);
    }

    /**
     * 테스트 단계 실행
     */
    @PostMapping("/{jobId}/test")
    @Operation(summary = "테스트 실행", description = "JUnit 테스트를 실행합니다.")
    public ResponseEntity<Map<String, Object>> runTests(@PathVariable String jobId) {
        Map<String, Object> result = pipelineService.testStep(jobId);
        return ResponseEntity.ok(result);
    }

    /**
     * 작업 상태 업데이트 (Airflow에서 호출)
     */
    @PutMapping("/{jobId}/status")
    @Operation(summary = "작업 상태 업데이트")
    public ResponseEntity<Void> updateJobStatus(
            @PathVariable String jobId,
            @RequestBody Map<String, Object> statusUpdate) {
        // TODO: 구현
        return ResponseEntity.ok().build();
    }

    /**
     * 상세 작업 상태 조회 (Airflow 상태 포함)
     */
    @GetMapping("/{jobId}/status/detailed")
    @Operation(summary = "상세 작업 상태", description = "Airflow 배치 상태를 포함한 상세 정보를 조회합니다.")
    public ResponseEntity<Map<String, Object>> getDetailedStatus(@PathVariable String jobId) {
        Map<String, Object> status = pipelineService.getJobStatus(jobId);
        return ResponseEntity.ok(status);
    }
}

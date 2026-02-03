package com.c2java.service;

import com.c2java.domain.ConversionJob;
import com.c2java.domain.ConversionJob.JobStatus;
import com.c2java.dto.ConversionRequest;
import com.c2java.dto.ConversionResponse;
import com.c2java.repository.ConversionJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * 변환 서비스
 * C to Java 변환 프로세스를 관리합니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ConversionService {

    private final ConversionJobRepository jobRepository;
    private final FileAnalysisService fileAnalysisService;
    private final LlmService llmService;
    private final CliService cliService;
    private final CompileService compileService;
    private final RuntimeTestService runtimeTestService;
    private final ReviewService reviewService;
    private final FileStorageService fileStorageService;
    private final ConversionPipelineService pipelineService;

    /**
     * 새로운 변환 작업 생성 (다중 파일)
     */
    @Transactional
    public ConversionResponse createConversionJob(List<MultipartFile> files, ConversionRequest request) throws IOException {
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("No files provided");
        }
        
        // 작업 ID 미리 생성
        String jobId = UUID.randomUUID().toString();
        
        log.info("Creating conversion job {} for {} files", jobId, files.size());
        
        // 첫 번째 파일을 대표 파일로 사용
        String firstFileName = files.get(0).getOriginalFilename();
        
        // 작업 생성
        ConversionJob job = ConversionJob.builder()
                .jobId(jobId)
                .jobName(request.getJobName() != null ? request.getJobName() : firstFileName)
                .sourcePath("/tmp/c2java/" + jobId)  // 임시 경로
                .targetLanguage(request.getTargetLanguage())
                .llmProvider(request.getLlmProvider())
                .status(JobStatus.PENDING)
                .build();
        
        job = jobRepository.save(job);
        
        // 파일 저장 및 경로 목록 생성
        java.util.List<java.nio.file.Path> savedPaths = new java.util.ArrayList<>();
        for (MultipartFile file : files) {
            String savedPath = saveUploadedFile(file, jobId);
            savedPaths.add(java.nio.file.Paths.get(savedPath));
            log.debug("Saved file: {}", savedPath);
        }
        
        // Airflow 파이프라인 시작
        pipelineService.startConversionWithAirflow(jobId, savedPaths);
        
        return ConversionResponse.fromEntity(job);
    }
    
    /**
     * 새로운 변환 작업 생성 (단일 파일 - 하위 호환성)
     */
    @Transactional
    public ConversionResponse createConversionJob(MultipartFile file, ConversionRequest request) throws IOException {
        return createConversionJob(List.of(file), request);
    }

    /**
     * 변환 프로세스 실행 (비동기)
     */
    @Async
    @Transactional
    public void startConversionProcess(String jobId) {
        // [참고] 새로운 파이프라인은 ConversionPipelineService 사용
        // 기존 코드는 호환성을 위해 유지
        ConversionJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));
        
        try {
            job.start();
            jobRepository.save(job);
            
            // 간단 변환 프로세스 (Airflow 없이)
            log.info("Starting simple conversion for job: {}", jobId);
            job.setStatus(JobStatus.ANALYZING);
            jobRepository.save(job);
            
            job.setStatus(JobStatus.CONVERTING);
            jobRepository.save(job);
            
            // TODO: 실제 변환 로직 구현
            // 현재는 PENDING 상태로 유지
            
            log.info("Conversion process initiated for job: {}", jobId);
            
        } catch (Exception e) {
            log.error("Conversion failed for job: {}", jobId, e);
            job.fail(e.getMessage());
            jobRepository.save(job);
        }
    }

    /**
     * 작업 상태 조회
     */
    @Transactional(readOnly = true)
    public ConversionResponse getJobStatus(UUID jobId) {
        ConversionJob job = jobRepository.findById(jobId.toString())
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));
        return ConversionResponse.fromEntity(job);
    }

    /**
     * 모든 작업 목록 조회
     */
    @Transactional(readOnly = true)
    public List<ConversionResponse> getAllJobs() {
        return jobRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(ConversionResponse::fromEntity)
                .toList();
    }

    /**
     * 상태별 작업 조회
     */
    @Transactional(readOnly = true)
    public List<ConversionResponse> getJobsByStatus(JobStatus status) {
        return jobRepository.findByStatusOrderByCreatedAtDesc(status).stream()
                .map(ConversionResponse::fromEntity)
                .toList();
    }

    /**
     * 업로드 파일 저장 (로컬 또는 원격 파일 서버)
     */
    private String saveUploadedFile(MultipartFile file, String jobId) throws IOException {
        return fileStorageService.uploadFile(file, jobId);
    }
}

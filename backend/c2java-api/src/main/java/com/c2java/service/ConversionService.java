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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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

    /**
     * 새로운 변환 작업 생성
     */
    @Transactional
    public ConversionResponse createConversionJob(MultipartFile file, ConversionRequest request) throws IOException {
        // 파일 저장
        String savedFilePath = saveUploadedFile(file);
        
        // 작업 생성
        ConversionJob job = ConversionJob.builder()
                .jobName(request.getJobName() != null ? request.getJobName() : file.getOriginalFilename())
                .sourcePath(savedFilePath)
                .llmProvider(request.getLlmProvider())
                .status(JobStatus.PENDING)
                .build();
        
        job = jobRepository.save(job);
        
        // 비동기 변환 프로세스 시작
        startConversionProcess(job.getId());
        
        return ConversionResponse.fromEntity(job);
    }

    /**
     * 변환 프로세스 실행 (비동기)
     */
    @Async
    @Transactional
    public void startConversionProcess(UUID jobId) {
        ConversionJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));
        
        try {
            job.start();
            jobRepository.save(job);
            
            // 1. 파일 구조 분석
            log.info("Starting file analysis for job: {}", jobId);
            job.setStatus(JobStatus.ANALYZING);
            jobRepository.save(job);
            fileAnalysisService.analyzeFile(job);
            
            // 2. C to Java 변환
            log.info("Starting conversion for job: {}", jobId);
            job.setStatus(JobStatus.CONVERTING);
            jobRepository.save(job);
            String outputPath = llmService.convertToJava(job);
            
            // 3. 컴파일 테스트
            log.info("Starting compile test for job: {}", jobId);
            job.setStatus(JobStatus.COMPILING);
            jobRepository.save(job);
            boolean compileSuccess = compileService.compileAndTest(job, outputPath);
            
            if (!compileSuccess && job.getCompileAttempts() < 3) {
                // 컴파일 실패 시 재변환
                job.incrementCompileAttempts();
                jobRepository.save(job);
                startConversionProcess(jobId);
                return;
            }
            
            // 4. 런타임 테스트
            log.info("Starting runtime test for job: {}", jobId);
            job.setStatus(JobStatus.TESTING);
            jobRepository.save(job);
            runtimeTestService.runTests(job, outputPath);
            
            // 5. 리뷰 생성
            log.info("Generating review for job: {}", jobId);
            job.setStatus(JobStatus.REVIEWING);
            jobRepository.save(job);
            reviewService.generateReview(job);
            
            // 완료
            job.complete(outputPath);
            jobRepository.save(job);
            log.info("Conversion completed for job: {}", jobId);
            
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
        ConversionJob job = jobRepository.findById(jobId)
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
     * 업로드 파일 저장
     */
    private String saveUploadedFile(MultipartFile file) throws IOException {
        String uploadDir = System.getProperty("conversion.workspace-dir", "/app/workspace");
        Path uploadPath = Paths.get(uploadDir, "uploads", UUID.randomUUID().toString());
        Files.createDirectories(uploadPath);
        
        Path filePath = uploadPath.resolve(file.getOriginalFilename());
        Files.copy(file.getInputStream(), filePath);
        
        return filePath.toString();
    }
}

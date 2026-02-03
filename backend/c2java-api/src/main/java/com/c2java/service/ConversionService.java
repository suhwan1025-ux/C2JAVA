package com.c2java.service;

import com.c2java.domain.ConversionJob;
import com.c2java.domain.ConversionJob.JobStatus;
import com.c2java.dto.ConversionRequest;
import com.c2java.dto.ConversionResponse;
import com.c2java.repository.ConversionJobRepository;
import com.c2java.util.SecurityUtil;
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
    private final AirflowApiService airflowApiService;
    private final SecurityUtil securityUtil;

    /**
     * 새로운 변환 작업 생성 (다중 파일)
     */
    @Transactional
    public ConversionResponse createConversionJob(List<MultipartFile> files, ConversionRequest request) throws IOException {
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("No files provided");
        }
        
        // 작업 ID 미리 생성
        UUID jobId = UUID.randomUUID();
        String jobIdStr = jobId.toString();
        
        log.info("Creating conversion job {} for {} files", jobIdStr, files.size());
        
        // 첫 번째 파일을 대표 파일로 사용
        String firstFileName = files.get(0).getOriginalFilename();
        
        // 현재 로그인한 사용자 ID 가져오기
        UUID currentUserId = securityUtil.getCurrentUserId();
        if (currentUserId == null) {
            throw new IllegalStateException("인증된 사용자를 찾을 수 없습니다.");
        }
        
        // 작업 생성
        ConversionJob job = ConversionJob.builder()
                .jobId(jobId)
                .jobName(request.getJobName() != null ? request.getJobName() : firstFileName)
                .userId(currentUserId)  // 사용자 ID 설정
                .sourcePath("/tmp/c2java/" + jobIdStr)  // 임시 경로
                .targetLanguage(request.getTargetLanguage())
                .llmProvider(request.getLlmProvider())
                .status(JobStatus.PENDING)
                .build();
        
        job = jobRepository.save(job);
        
        // 파일 저장 및 경로 목록 생성
        java.util.List<java.nio.file.Path> savedPaths = new java.util.ArrayList<>();
        String userId = currentUserId.toString();
        String projectName = job.getJobName();
        
        for (MultipartFile file : files) {
            String savedPath = saveUploadedFile(file, jobIdStr, userId, projectName);
            savedPaths.add(java.nio.file.Paths.get(savedPath));
            log.debug("Saved file: {} to {}", file.getOriginalFilename(), savedPath);
        }
        
        // 작업의 sourcePath를 실제 저장 경로로 업데이트
        if (!savedPaths.isEmpty()) {
            job.setSourcePath(savedPaths.get(0).getParent().toString());
            job = jobRepository.save(job);
        }
        
        // Airflow 파이프라인 시작
        pipelineService.startConversionWithAirflow(jobIdStr, savedPaths);
        
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
        UUID jobUuid = UUID.fromString(jobId);
        ConversionJob job = jobRepository.findById(jobUuid)
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
     * 작업 상태 조회 (권한 확인)
     */
    @Transactional(readOnly = true)
    public ConversionResponse getJobStatus(UUID jobId) {
        ConversionJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));
        
        // 권한 확인: 본인 작업이거나 관리자만 조회 가능
        UUID currentUserId = securityUtil.getCurrentUserId();
        boolean isAdmin = securityUtil.isCurrentUserAdmin();
        
        if (!isAdmin && !job.getUserId().equals(currentUserId)) {
            throw new IllegalArgumentException("이 작업에 대한 접근 권한이 없습니다.");
        }
        
        return ConversionResponse.fromEntity(job);
    }

    /**
     * 모든 작업 목록 조회 (본인 것만 또는 관리자는 전체)
     */
    @Transactional(readOnly = true)
    public List<ConversionResponse> getAllJobs() {
        UUID currentUserId = securityUtil.getCurrentUserId();
        boolean isAdmin = securityUtil.isCurrentUserAdmin();
        
        if (isAdmin) {
            // 관리자는 모든 작업 조회
            log.debug("Admin user - returning all jobs");
            return jobRepository.findAllByOrderByCreatedAtDesc().stream()
                    .map(ConversionResponse::fromEntity)
                    .toList();
        } else {
            // 일반 사용자는 본인 작업만 조회
            if (currentUserId == null) {
                log.warn("No authenticated user - returning empty list");
                return List.of();
            }
            log.debug("Regular user {} - returning own jobs only", currentUserId);
            return jobRepository.findByUserIdOrderByCreatedAtDesc(currentUserId).stream()
                    .map(ConversionResponse::fromEntity)
                    .toList();
        }
    }

    /**
     * 상태별 작업 조회 (본인 것만 또는 관리자는 전체)
     */
    @Transactional(readOnly = true)
    public List<ConversionResponse> getJobsByStatus(JobStatus status) {
        UUID currentUserId = securityUtil.getCurrentUserId();
        boolean isAdmin = securityUtil.isCurrentUserAdmin();
        
        List<ConversionJob> jobs = jobRepository.findByStatusOrderByCreatedAtDesc(status);
        
        if (!isAdmin && currentUserId != null) {
            // 일반 사용자는 본인 작업만 필터링
            jobs = jobs.stream()
                    .filter(job -> currentUserId.equals(job.getUserId()))
                    .toList();
        }
        
        return jobs.stream()
                .map(ConversionResponse::fromEntity)
                .toList();
    }

    /**
     * 변환 작업 취소 (권한 확인)
     */
    @Transactional
    public ConversionResponse cancelJob(UUID jobId) {
        ConversionJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));
        
        // 권한 확인: 본인 작업이거나 관리자만 취소 가능
        UUID currentUserId = securityUtil.getCurrentUserId();
        boolean isAdmin = securityUtil.isCurrentUserAdmin();
        
        if (!isAdmin && !job.getUserId().equals(currentUserId)) {
            throw new IllegalArgumentException("이 작업에 대한 접근 권한이 없습니다.");
        }
        
        log.info("Cancelling conversion job: {} by user: {}", jobId, currentUserId);
        job.appendLog("✓ 작업 취소 요청을 받았습니다.");
        
        // 이미 완료/실패/취소된 작업은 취소 불가
        if (job.getStatus() == JobStatus.COMPLETED || 
            job.getStatus() == JobStatus.FAILED || 
            job.getStatus() == JobStatus.CANCELLED) {
            throw new IllegalStateException("작업을 취소할 수 없는 상태입니다: " + job.getStatus());
        }
        
        // 작업 상태를 CANCELLED로 즉시 변경 (Airflow 취소 실패해도 DB는 취소 상태로)
        JobStatus previousStatus = job.getStatus();
        job.setStatus(JobStatus.CANCELLED);
        job.setErrorMessage("사용자에 의해 취소됨");
        job.updateProgress("CANCELLED", 0);
        job = jobRepository.save(job);
        log.info("Job status changed to CANCELLED: {}", jobId);
        
        // Airflow DAG Run 취소 시도 (Best effort - 실패해도 작업은 취소됨)
        if (job.getAirflowDagId() != null && job.getAirflowRunId() != null) {
            try {
                log.info("Attempting to cancel Airflow DAG Run: {} / {}", 
                        job.getAirflowDagId(), job.getAirflowRunId());
                job.appendLog("Airflow 워크플로우를 중단하는 중...");
                jobRepository.save(job);
                
                // 1. 실행 중인 Task들을 취소
                airflowApiService.cancelDagRun(job.getAirflowDagId(), job.getAirflowRunId());
                job.appendLog("✓ 실행 중인 Task들이 중단되었습니다.");
                jobRepository.save(job);
                
                // 2. DAG Run 완전 삭제 (선택적)
                try {
                    airflowApiService.deleteDagRun(job.getAirflowDagId(), job.getAirflowRunId());
                    job.appendLog("✓ Airflow 워크플로우가 완전히 제거되었습니다.");
                } catch (Exception deleteEx) {
                    log.debug("Could not delete DAG Run (this is optional): {}", deleteEx.getMessage());
                    job.appendLog("ℹ DAG Run 삭제는 선택적이므로 건너뜁니다.");
                }
                
            } catch (Exception e) {
                log.warn("Failed to cancel Airflow DAG Run, but job is already marked as cancelled", e);
                job.appendLog("⚠ Airflow 취소 실패: " + e.getMessage());
                job.appendLog("ℹ Airflow 서버에 연결할 수 없지만, 작업은 취소 상태로 표시됩니다.");
            }
        } else {
            log.info("No Airflow DAG info, job cancelled locally");
            job.appendLog("ℹ Airflow 워크플로우 정보가 없습니다. 로컬 취소 완료.");
        }
        
        job.appendLog("✓ 작업이 성공적으로 취소되었습니다.");
        job = jobRepository.save(job);
        
        log.info("Job cancelled successfully: {}", jobId);
        return ConversionResponse.fromEntity(job);
    }
    
    /**
     * 작업에 로그 추가 (권한 확인)
     */
    @Transactional
    public void appendJobLog(UUID jobId, String message) {
        ConversionJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));
        
        job.appendLog(message);
        jobRepository.save(job);
    }

    /**
     * 업로드 파일 저장 (로컬 또는 원격 파일 서버)
     */
    private String saveUploadedFile(MultipartFile file, String jobId, String userId, String projectName) throws IOException {
        return fileStorageService.uploadFile(file, jobId, userId, projectName);
    }
    
    /**
     * 하위 호환성을 위한 메서드
     */
    @Deprecated
    private String saveUploadedFile(MultipartFile file, String jobId) throws IOException {
        return fileStorageService.uploadFile(file, jobId);
    }
}

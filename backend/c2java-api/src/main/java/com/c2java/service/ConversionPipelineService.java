package com.c2java.service;

import com.c2java.domain.ConversionJob;
import com.c2java.domain.AnalysisResult;
import com.c2java.dto.CFileStructure;
import com.c2java.repository.ConversionJobRepository;
import com.c2java.repository.AnalysisResultRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * 변환 파이프라인 오케스트레이션 서비스
 * 전체 변환 프로세스를 통합 관리
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ConversionPipelineService {

    private final ConversionJobRepository jobRepository;
    private final AnalysisResultRepository analysisRepository;
    private final CFileAnalyzerService analyzerService;
    private final CodeConverterService converterService;
    private final AirflowDagService airflowDagService;
    private final AirflowApiService airflowApiService;
    private final GradleBuildService buildService;
    private final TestRunnerService testService;
    private final ObjectMapper objectMapper;

    /**
     * 변환 작업 시작 (Airflow 사용)
     */
    @Async
    @Transactional
    public void startConversionWithAirflow(String jobId, List<Path> sourceFiles) {
        ConversionJob job = jobRepository.findByJobId(UUID.fromString(jobId))
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));
        
        try {
            log.info("Starting conversion pipeline for job: {}", jobId);
            job.appendLog("변환 파이프라인을 시작합니다...");
            
            // 1. Airflow DAG 생성 (파일 개수 전달)
            job.setStatus(ConversionJob.JobStatus.PENDING);
            job.updateProgress("INITIALIZE", 5);
            jobRepository.save(job);
            
            int fileCount = sourceFiles != null ? sourceFiles.size() : 0;
            log.info("Creating DAG for {} files", fileCount);
            job.appendLog(String.format("총 %d개의 파일을 변환합니다.", fileCount));
            
            String dagId = airflowDagService.createConversionDag(job, fileCount);
            job.setAirflowDagId(dagId);
            job.appendLog("Airflow DAG가 생성되었습니다: " + dagId);
            jobRepository.save(job);
            
            // 2. DAG 트리거
            Map<String, Object> config = Map.of(
                    "job_id", jobId,
                    "target_language", job.getTargetLanguage(),
                    "source_files", sourceFiles.stream().map(Path::toString).toList()
            );
            
            job.appendLog("Airflow 워크플로우를 시작합니다...");
            Map<String, Object> dagRun = airflowApiService.triggerDag(dagId, config);
            job.setAirflowRunId((String) dagRun.get("dag_run_id"));
            job.appendLog("워크플로우가 시작되었습니다: " + dagRun.get("dag_run_id"));
            jobRepository.save(job);
            
            log.info("Airflow DAG triggered: {} / {}", dagId, dagRun.get("dag_run_id"));
            
        } catch (Exception e) {
            log.error("Pipeline start failed", e);
            job.appendLog("파이프라인 시작 실패: " + e.getMessage());
            job.markFailed("파이프라인 시작 실패: " + e.getMessage());
            jobRepository.save(job);
        }
    }

    /**
     * 파일 분석 단계 (Airflow Task에서 호출)
     */
    @Transactional
    public Map<String, Object> analyzeStep(String jobId) {
        ConversionJob job = jobRepository.findByJobId(UUID.fromString(jobId))
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));
        
        // 취소 상태 확인
        if (job.getStatus() == ConversionJob.JobStatus.CANCELLED) {
            log.info("Job {} is cancelled, skipping analyze step", jobId);
            throw new RuntimeException("작업이 취소되었습니다.");
        }
        
        try {
            job.setStatus(ConversionJob.JobStatus.ANALYZING);
            job.updateProgress("ANALYZE", 10);
            job.appendLog("파일 분석을 시작합니다...");
            jobRepository.save(job);
            
            // 소스 파일 로드
            List<Path> sourceFiles = loadSourceFiles(job);
            
            // 각 파일 분석
            int functionCount = 0;
            int structCount = 0;
            int sqlCount = 0;
            
            for (Path file : sourceFiles) {
                CFileStructure structure = analyzerService.analyzeFile(file);
                
                // 분석 결과 저장
                AnalysisResult analysis = AnalysisResult.builder()
                        .jobId(jobId)
                        .sourceFile(file.getFileName().toString())
                        .fileType(structure.getFileType())
                        .functions(toJson(structure.getFunctions()))
                        .structs(toJson(structure.getStructs()))
                        .enums(toJson(structure.getEnums()))
                        .sqlQueries(toJson(structure.getSqlQueries()))
                        .includes(toJson(structure.getIncludes()))
                        .defines(toJson(structure.getDefines()))
                        .lineCount(structure.getLineCount())
                        .functionCount(structure.getFunctions().size())
                        .structCount(structure.getStructs().size())
                        .build();
                
                analysisRepository.save(analysis);
                
                functionCount += structure.getFunctions().size();
                structCount += structure.getStructs().size();
                sqlCount += structure.getSqlQueries().size();
            }
            
            // 작업 정보 업데이트
            job.setFunctionCount(functionCount);
            job.setStructCount(structCount);
            job.setSqlCount(sqlCount);
            job.updateProgress("ANALYZE", 25);
            jobRepository.save(job);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("function_count", functionCount);
            result.put("struct_count", structCount);
            result.put("sql_count", sqlCount);
            
            return result;
            
        } catch (Exception e) {
            log.error("Analysis failed", e);
            // 취소된 경우는 실패로 표시하지 않음
            if (job.getStatus() != ConversionJob.JobStatus.CANCELLED) {
                job.appendLog("분석 실패: " + e.getMessage());
                job.markFailed("분석 실패: " + e.getMessage());
                jobRepository.save(job);
            }
            throw new RuntimeException(e);
        }
    }

    /**
     * 코드 변환 단계
     */
    @Transactional
    public Map<String, Object> convertStep(String jobId) {
        ConversionJob job = jobRepository.findByJobId(UUID.fromString(jobId))
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));
        
        // 취소 상태 확인
        if (job.getStatus() == ConversionJob.JobStatus.CANCELLED) {
            log.info("Job {} is cancelled, skipping convert step", jobId);
            throw new RuntimeException("작업이 취소되었습니다.");
        }
        
        try {
            job.setStatus(ConversionJob.JobStatus.CONVERTING);
            job.updateProgress("CONVERT", 30);
            job.appendLog("코드 변환을 시작합니다...");
            jobRepository.save(job);
            
            // 소스 파일 로드
            List<Path> sourceFiles = loadSourceFiles(job);
            job.appendLog(String.format("%d개 파일을 Java로 변환합니다...", sourceFiles.size()));
            jobRepository.save(job);
            
            // 취소 상태 재확인
            job = jobRepository.findByJobId(UUID.fromString(jobId))
                    .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));
            if (job.getStatus() == ConversionJob.JobStatus.CANCELLED) {
                throw new RuntimeException("작업이 취소되었습니다.");
            }
            
            // Java 코드 생성
            Map<String, String> generatedFiles = converterService.convertCFiles(job, sourceFiles);
            job.appendLog(String.format("%d개의 Java 파일이 생성되었습니다.", generatedFiles.size()));
            jobRepository.save(job);
            
            // 생성된 파일 저장
            Path outputPath = Paths.get("/tmp/c2java/output/" + jobId);
            Files.createDirectories(outputPath);
            
            for (Map.Entry<String, String> entry : generatedFiles.entrySet()) {
                Path filePath = outputPath.resolve(entry.getKey());
                Files.createDirectories(filePath.getParent());
                Files.writeString(filePath, entry.getValue());
            }
            
            job.setGeneratedFileCount(generatedFiles.size());
            job.setOutputPath(outputPath.toString());
            job.updateProgress("CONVERT", 60);
            jobRepository.save(job);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("generated_files", generatedFiles.size());
            result.put("output_path", outputPath.toString());
            
            return result;
            
        } catch (Exception e) {
            log.error("Conversion failed", e);
            // 취소된 경우는 실패로 표시하지 않음
            if (job.getStatus() != ConversionJob.JobStatus.CANCELLED) {
                job.appendLog("변환 실패: " + e.getMessage());
                job.markFailed("변환 실패: " + e.getMessage());
                jobRepository.save(job);
            }
            throw new RuntimeException(e);
        }
    }

    /**
     * 컴파일 단계
     */
    @Transactional
    public Map<String, Object> compileStep(String jobId) {
        ConversionJob job = jobRepository.findByJobId(UUID.fromString(jobId))
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));
        
        // 취소 상태 확인
        if (job.getStatus() == ConversionJob.JobStatus.CANCELLED) {
            log.info("Job {} is cancelled, skipping compile step", jobId);
            throw new RuntimeException("작업이 취소되었습니다.");
        }
        
        try {
            job.setStatus(ConversionJob.JobStatus.COMPILING);
            job.updateProgress("COMPILE", 65);
            job.appendLog("컴파일을 시작합니다...");
            jobRepository.save(job);
            
            Path projectPath = Paths.get(job.getOutputPath());
            Map<String, Object> buildResult = buildService.buildProject(projectPath);
            
            boolean success = (boolean) buildResult.get("success");
            job.setCompileSuccess(success);
            
            if (success) {
                job.appendLog("컴파일이 성공했습니다.");
            } else {
                List<String> errors = (List<String>) buildResult.get("errors");
                job.setCompileErrors(String.join("\n", errors));
                job.appendLog("컴파일 오류가 발생했습니다: " + errors.size() + "개");
            }
            
            job.updateProgress("COMPILE", 80);
            jobRepository.save(job);
            
            return buildResult;
            
        } catch (Exception e) {
            log.error("Compilation failed", e);
            // 취소된 경우는 실패로 표시하지 않음
            if (job.getStatus() != ConversionJob.JobStatus.CANCELLED) {
                job.appendLog("컴파일 실패: " + e.getMessage());
                job.setCompileSuccess(false);
                job.setCompileErrors(e.getMessage());
                jobRepository.save(job);
            }
            throw new RuntimeException(e);
        }
    }

    /**
     * 테스트 단계
     */
    @Transactional
    public Map<String, Object> testStep(String jobId) {
        ConversionJob job = jobRepository.findByJobId(UUID.fromString(jobId))
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));
        
        // 취소 상태 확인
        if (job.getStatus() == ConversionJob.JobStatus.CANCELLED) {
            log.info("Job {} is cancelled, skipping test step", jobId);
            throw new RuntimeException("작업이 취소되었습니다.");
        }
        
        try {
            job.setStatus(ConversionJob.JobStatus.TESTING);
            job.updateProgress("TEST", 85);
            job.appendLog("테스트를 시작합니다...");
            jobRepository.save(job);
            
            Path projectPath = Paths.get(job.getOutputPath());
            Map<String, Object> testResult = testService.runTests(projectPath);
            
            boolean success = (boolean) testResult.get("success");
            job.setTestSuccess(success);
            job.setTestResults(testResult.toString());
            
            if (success) {
                job.appendLog("모든 테스트가 통과했습니다.");
            } else {
                job.appendLog("일부 테스트가 실패했습니다.");
            }
            
            job.updateProgress("TEST", 95);
            jobRepository.save(job);
            
            return testResult;
            
        } catch (Exception e) {
            log.error("Testing failed", e);
            // 취소된 경우는 실패로 표시하지 않음
            if (job.getStatus() != ConversionJob.JobStatus.CANCELLED) {
                job.appendLog("테스트 실패: " + e.getMessage());
                job.setTestSuccess(false);
                job.setTestResults("테스트 실패: " + e.getMessage());
                jobRepository.save(job);
            }
            throw new RuntimeException(e);
        }
    }

    /**
     * 작업 상태 조회 (Airflow 상태 포함)
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getJobStatus(String jobId) {
        ConversionJob job = jobRepository.findByJobId(UUID.fromString(jobId))
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));
        
        Map<String, Object> status = new HashMap<>();
        status.put("jobId", job.getJobId());
        status.put("jobName", job.getJobName());
        status.put("status", job.getStatus());
        status.put("currentStage", job.getCurrentStage());
        status.put("progress", job.getProgress());
        status.put("targetLanguage", job.getTargetLanguage());
        
        // Airflow 상태
        if (job.getAirflowDagId() != null && job.getAirflowRunId() != null) {
            try {
                Map<String, Object> dagRunStatus = airflowApiService.getDagRunStatus(
                        job.getAirflowDagId(), 
                        job.getAirflowRunId()
                );
                status.put("airflowStatus", dagRunStatus);
                
                // Task 상태들
                Map<String, Object> taskInstances = airflowApiService.getAllTaskInstances(
                        job.getAirflowDagId(), 
                        job.getAirflowRunId()
                );
                status.put("tasks", taskInstances.get("task_instances"));
                
            } catch (Exception e) {
                log.warn("Failed to get Airflow status", e);
                status.put("airflowStatus", Map.of("state", "unavailable"));
            }
        }
        
        // 분석 결과
        List<AnalysisResult> analysisResults = analysisRepository.findByJobId(jobId);
        status.put("analysisResults", analysisResults);
        
        // 통계
        status.put("functionCount", job.getFunctionCount());
        status.put("structCount", job.getStructCount());
        status.put("sqlCount", job.getSqlCount());
        status.put("generatedFileCount", job.getGeneratedFileCount());
        status.put("compileSuccess", job.getCompileSuccess());
        status.put("testSuccess", job.getTestSuccess());
        
        return status;
    }

    /**
     * 소스 파일 로드
     */
    private List<Path> loadSourceFiles(ConversionJob job) throws IOException {
        Path sourcePath = Paths.get(job.getSourceFilePath());
        
        if (Files.isDirectory(sourcePath)) {
            return Files.list(sourcePath)
                    .filter(p -> p.toString().endsWith(".c") || 
                                p.toString().endsWith(".h") || 
                                p.toString().endsWith(".pc"))
                    .toList();
        } else {
            return List.of(sourcePath);
        }
    }

    /**
     * JSON 변환
     */
    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "[]";
        }
    }
}

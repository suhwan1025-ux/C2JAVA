package com.c2java.service;

import com.c2java.domain.ConversionJob;
import com.c2java.domain.AnalysisResult;
import com.c2java.dto.CFileStructure;
import com.c2java.event.JobCreatedEvent;
import com.c2java.repository.ConversionJobRepository;
import com.c2java.repository.AnalysisResultRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
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
    private final com.c2java.repository.FileConversionResultRepository fileResultRepository;

    /**
     * Job 객체로 변환 시작
     */
    public void startConversionWithJob(ConversionJob job, List<Path> sourceFiles) {
        log.info("🚀 [AIRFLOW] Starting pipeline for job: {}", job.getJobId());
        
        try {
            job.appendLog("변환 파이프라인을 시작합니다...");
            log.info("✅ [AIRFLOW] appendLog succeeded");
            
            // 1. Airflow DAG 생성 (파일 개수 전달)
            job.setStatus(ConversionJob.JobStatus.PENDING);
            job.updateProgress("INITIALIZE", 5);
            jobRepository.save(job);
            
            int fileCount = sourceFiles != null ? sourceFiles.size() : 0;
            log.info("Creating DAG for {} files", fileCount);
            job.appendLog(String.format("총 %d개의 파일을 변환합니다.", fileCount));
            
            try {
                String dagId = airflowDagService.createConversionDag(job, fileCount);
                job.setAirflowDagId(dagId);
                job.appendLog("✅ Airflow DAG가 생성되었습니다: " + dagId);
                jobRepository.save(job);
                log.info("✅ DAG created successfully: {}", dagId);
            } catch (Exception e) {
                log.error("❌ Failed to create DAG", e);
                job.appendLog("❌ DAG 생성 실패: " + e.getMessage());
                job.appendLog("상세: " + e.getClass().getSimpleName());
                throw e;
            }
            
            // DAG가 Airflow에 등록될 때까지 대기 (최대 90초)
            job.appendLog("⏳ Airflow가 DAG를 인식하는 중...");
            jobRepository.save(job);
            
            boolean dagRegistered = false;
            for (int i = 0; i < 30; i++) {  // 30번 시도 (3초마다 = 최대 90초)
                try {
                    Thread.sleep(3000);  // 3초 대기
                    // DAG 존재 여부를 체크하는 간단한 API 호출
                    try {
                        Map<String, Object> dagInfo = airflowApiService.getDagInfo(job.getAirflowDagId());
                        if (dagInfo != null && dagInfo.containsKey("dag_id")) {
                            dagRegistered = true;
                            log.info("✅ [AIRFLOW] DAG registered after {} seconds", (i + 1) * 3);
                            job.appendLog(String.format("✅ DAG 등록 완료 (%d초 소요)", (i + 1) * 3));
                            jobRepository.save(job);
                            break;
                        }
                    } catch (Exception e) {
                        // 404는 아직 등록 안 된 것, 계속 대기
                        if (i % 5 == 0) {  // 15초마다 로그
                            log.info("⏳ [AIRFLOW] Waiting for DAG registration... ({}s elapsed)", (i + 1) * 3);
                        }
                        if (i == 29) {  // 마지막 시도
                            log.warn("⚠️ [AIRFLOW] DAG not registered after 90 seconds");
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted while waiting for DAG registration", e);
                }
            }
            
            if (!dagRegistered) {
                job.appendLog("⚠️ DAG 등록 대기 시간 초과 (90초), 트리거를 시도합니다...");
                jobRepository.save(job);
            }
            
            // 2. DAG 트리거
            Map<String, Object> config = Map.of(
                    "job_id", job.getJobId().toString(),
                    "target_language", job.getTargetLanguage(),
                    "source_files", sourceFiles.stream().map(Path::toString).toList()
            );
            
            job.appendLog("Airflow 워크플로우를 시작합니다...");
            
            try {
                Map<String, Object> dagRun = airflowApiService.triggerDag(job.getAirflowDagId(), config);
                job.setAirflowRunId((String) dagRun.get("dag_run_id"));
                job.appendLog("✅ 워크플로우가 시작되었습니다: " + dagRun.get("dag_run_id"));
                jobRepository.save(job);
                log.info("✅ Airflow DAG triggered: {} / {}", job.getAirflowDagId(), dagRun.get("dag_run_id"));
            } catch (Exception e) {
                log.error("❌ Failed to trigger DAG", e);
                job.appendLog("❌ 워크플로우 시작 실패: " + e.getMessage());
                job.appendLog("상세: " + e.getClass().getSimpleName());
                throw e;
            }
            
        } catch (Exception e) {
            log.error("❌ Pipeline start failed for job: {}", job.getJobId(), e);
            job.appendLog("");
            job.appendLog("❌ 파이프라인 시작 실패");
            job.appendLog("오류 유형: " + e.getClass().getSimpleName());
            job.appendLog("오류 메시지: " + e.getMessage());
            if (e.getCause() != null) {
                job.appendLog("근본 원인: " + e.getCause().getMessage());
            }
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

    // ============= 파일별 개별 처리 메서드 =============

    /**
     * Job의 파일 목록 조회
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getJobFiles(String jobId) {
        // sourcePath만 조회 (LOB 필드 접근 방지)
        ConversionJob job = jobRepository.findByJobId(UUID.fromString(jobId))
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));
        
        String sourcePathStr = job.getSourcePath();
        
        try {
            Path sourcePath = Paths.get(sourcePathStr);
            List<String> files = new ArrayList<>();
            
            if (Files.exists(sourcePath)) {
                Files.walk(sourcePath)
                        .filter(Files::isRegularFile)
                        .filter(p -> p.toString().endsWith(".c") || p.toString().endsWith(".h"))
                        .forEach(p -> files.add(p.toString()));
            }
            
            return Map.of(
                    "jobId", jobId,
                    "files", files,
                    "fileCount", files.size()
            );
        } catch (IOException e) {
            log.error("Failed to get file list for job: {}", jobId, e);
            return Map.of("files", Collections.emptyList(), "error", e.getMessage());
        }
    }

    /**
     * 파일별 분석
     */
    @Transactional
    public Map<String, Object> analyzeFile(String jobId, String fileName) {
        ConversionJob job = jobRepository.findByJobId(UUID.fromString(jobId))
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));
        
        // 파일 결과 레코드 조회 또는 생성
        com.c2java.domain.FileConversionResult fileResult = fileResultRepository
                .findByJobIdAndSourceFileName(job.getJobId(), fileName);
        
        if (fileResult == null) {
            fileResult = com.c2java.domain.FileConversionResult.builder()
                    .jobId(job.getJobId())
                    .sourceFileName(fileName)
                    .status(com.c2java.domain.FileConversionResult.FileStatus.ANALYZING)
                    .currentStage(com.c2java.domain.FileConversionResult.FileStage.ANALYZE)
                    .progress(10)
                    .build();
        } else {
            fileResult.setStatus(com.c2java.domain.FileConversionResult.FileStatus.ANALYZING);
            fileResult.setCurrentStage(com.c2java.domain.FileConversionResult.FileStage.ANALYZE);
            fileResult.setProgress(10);
        }
        
        fileResult.appendLog("📊 파일 분석 시작: " + fileName);
        fileResultRepository.save(fileResult);
        
        try {
            // 파일 찾기
            Path sourcePath = Paths.get(job.getSourcePath());
            Path filePath = Files.walk(sourcePath)
                    .filter(p -> p.getFileName().toString().equals(fileName))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("File not found: " + fileName));
            
            fileResult.setSourceFilePath(filePath.toString());
            
            // 분석 실행
            CFileStructure structure = analyzerService.analyzeFile(filePath);
            
            // 분석 결과 저장
            fileResult.setAnalyzeResult(toJson(Map.of(
                    "functions", structure.getFunctions().size(),
                    "structs", structure.getStructs().size(),
                    "lines", structure.getLineCount()
            )));
            fileResult.updateStage(
                    com.c2java.domain.FileConversionResult.FileStage.ANALYZE,
                    com.c2java.domain.FileConversionResult.FileStatus.ANALYZING,
                    25
            );
            fileResult.appendLog("✅ 분석 완료: 함수 " + structure.getFunctions().size() + 
                               "개, 구조체 " + structure.getStructs().size() + "개");
            fileResultRepository.save(fileResult);
            
            return Map.of(
                    "status", "success",
                    "file", fileName,
                    "structure", structure
            );
            
        } catch (Exception e) {
            log.error("파일 분석 실패: {}", fileName, e);
            fileResult.markFailed("분석 실패: " + e.getMessage());
            fileResultRepository.save(fileResult);
            
            return Map.of(
                    "status", "failed",
                    "file", fileName,
                    "error", e.getMessage()
            );
        }
    }

    /**
     * 파일별 변환
     */
    @Transactional
    public Map<String, Object> convertFile(String jobId, String fileName) {
        ConversionJob job = jobRepository.findByJobId(UUID.fromString(jobId))
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));
        
        com.c2java.domain.FileConversionResult fileResult = fileResultRepository
                .findByJobIdAndSourceFileName(job.getJobId(), fileName);
        
        if (fileResult == null) {
            throw new IllegalStateException("파일 분석이 먼저 실행되어야 합니다: " + fileName);
        }
        
        fileResult.setStatus(com.c2java.domain.FileConversionResult.FileStatus.CONVERTING);
        fileResult.setCurrentStage(com.c2java.domain.FileConversionResult.FileStage.CONVERT);
        fileResult.setProgress(40);
        fileResult.appendLog("🔄 Java 변환 시작: " + fileName);
        fileResultRepository.save(fileResult);
        
        try {
            Path filePath = Paths.get(fileResult.getSourceFilePath());
            
            // 변환 로그를 기록하기 위한 StringBuilder 생성
            StringBuilder conversionLog = new StringBuilder();
            conversionLog.append("\n📝 코드 변환 로그\n");
            conversionLog.append("═══════════════════════════════════════\n");
            
            // 변환 실행 (로그 포함)
            Map<String, String> generatedFiles = converterService.convertCFiles(
                    job,
                    List.of(filePath),
                    conversionLog
            );
            
            // 변환 로그를 FileConversionResult에 추가
            if (conversionLog.length() > 0) {
                fileResult.appendLog(conversionLog.toString());
            }
            
            // 생성된 파일 경로 저장
            String javaFile = generatedFiles.keySet().iterator().next();
            fileResult.setOutputFilePath(javaFile);
            fileResult.setConvertResult(toJson(Map.of(
                    "generatedFiles", generatedFiles.keySet(),
                    "linesConverted", generatedFiles.values().iterator().next().split("\n").length
            )));
            fileResult.updateStage(
                    com.c2java.domain.FileConversionResult.FileStage.CONVERT,
                    com.c2java.domain.FileConversionResult.FileStatus.CONVERTING,
                    55
            );
            fileResult.appendLog("✅ 변환 완료: " + javaFile);
            fileResultRepository.save(fileResult);
            
            return Map.of(
                    "status", "success",
                    "file", fileName,
                    "output", javaFile
            );
            
        } catch (Exception e) {
            log.error("파일 변환 실패: {}", fileName, e);
            fileResult.markFailed("변환 실패: " + e.getMessage());
            fileResultRepository.save(fileResult);
            
            return Map.of(
                    "status", "failed",
                    "file", fileName,
                    "error", e.getMessage()
            );
        }
    }

    /**
     * 파일별 컴파일
     */
    @Transactional
    public Map<String, Object> compileFile(String jobId, String fileName) {
        ConversionJob job = jobRepository.findByJobId(UUID.fromString(jobId))
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));
        
        com.c2java.domain.FileConversionResult fileResult = fileResultRepository
                .findByJobIdAndSourceFileName(job.getJobId(), fileName);
        
        if (fileResult == null || fileResult.getOutputFilePath() == null) {
            throw new IllegalStateException("파일 변환이 먼저 실행되어야 합니다: " + fileName);
        }
        
        fileResult.setStatus(com.c2java.domain.FileConversionResult.FileStatus.COMPILING);
        fileResult.setCurrentStage(com.c2java.domain.FileConversionResult.FileStage.COMPILE);
        fileResult.setProgress(70);
        fileResult.appendLog("🔨 컴파일 시작: " + fileName);
        fileResultRepository.save(fileResult);
        
        try {
            Path projectPath = Paths.get(job.getOutputPath());
            Map<String, Object> buildResult = buildService.buildProject(projectPath);
            
            boolean success = (boolean) buildResult.get("success");
            fileResult.setCompileSuccess(success);
            
            if (!success) {
                List<String> errors = (List<String>) buildResult.get("errors");
                fileResult.setCompileErrors(String.join("\n", errors));
                fileResult.appendLog("⚠️ 컴파일 오류: " + errors.size() + "개");
            } else {
                fileResult.appendLog("✅ 컴파일 완료");
            }
            
            fileResult.updateStage(
                    com.c2java.domain.FileConversionResult.FileStage.COMPILE,
                    com.c2java.domain.FileConversionResult.FileStatus.COMPILING,
                    80
            );
            fileResultRepository.save(fileResult);
            
            return Map.of(
                    "status", success ? "success" : "failed",
                    "file", fileName,
                    "compileSuccess", success,
                    "errors", buildResult.get("errors")
            );
            
        } catch (Exception e) {
            log.error("파일 컴파일 실패: {}", fileName, e);
            fileResult.markFailed("컴파일 실패: " + e.getMessage());
            fileResultRepository.save(fileResult);
            
            return Map.of(
                    "status", "failed",
                    "file", fileName,
                    "error", e.getMessage()
            );
        }
    }

    /**
     * 파일별 테스트
     */
    @Transactional
    public Map<String, Object> testFile(String jobId, String fileName) {
        ConversionJob job = jobRepository.findByJobId(UUID.fromString(jobId))
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));
        
        com.c2java.domain.FileConversionResult fileResult = fileResultRepository
                .findByJobIdAndSourceFileName(job.getJobId(), fileName);
        
        if (fileResult == null || !Boolean.TRUE.equals(fileResult.getCompileSuccess())) {
            throw new IllegalStateException("컴파일이 먼저 성공해야 합니다: " + fileName);
        }
        
        fileResult.setStatus(com.c2java.domain.FileConversionResult.FileStatus.TESTING);
        fileResult.setCurrentStage(com.c2java.domain.FileConversionResult.FileStage.TEST);
        fileResult.setProgress(90);
        fileResult.appendLog("🧪 테스트 시작: " + fileName);
        fileResultRepository.save(fileResult);
        
        try {
            Path projectPath = Paths.get(job.getOutputPath());
            Map<String, Object> testResult = testService.runTests(projectPath);
            
            boolean success = (boolean) testResult.get("success");
            fileResult.setTestSuccess(success);
            fileResult.setTestResults(toJson(testResult));
            
            if (success) {
                fileResult.markCompleted();
                fileResult.appendLog("✅ 테스트 완료 - 모든 단계 성공!");
            } else {
                fileResult.appendLog("⚠️ 일부 테스트 실패");
                fileResult.setProgress(95);
            }
            
            fileResultRepository.save(fileResult);
            
            return Map.of(
                    "status", success ? "success" : "failed",
                    "file", fileName,
                    "testSuccess", success,
                    "results", testResult
            );
            
        } catch (Exception e) {
            log.error("파일 테스트 실패: {}", fileName, e);
            fileResult.markFailed("테스트 실패: " + e.getMessage());
            fileResultRepository.save(fileResult);
            
            return Map.of(
                    "status", "failed",
                    "file", fileName,
                    "error", e.getMessage()
            );
        }
    }

    /**
     * 파일별 변환 결과 목록
     */
    public List<Map<String, Object>> getFileResults(String jobId) {
        List<com.c2java.domain.FileConversionResult> results = fileResultRepository
                .findByJobIdOrderByCreatedAt(UUID.fromString(jobId));
        
        return results.stream()
                .map(r -> Map.of(
                        "fileName", r.getSourceFileName(),
                        "status", r.getStatus().toString(),
                        "stage", r.getCurrentStage().toString(),
                        "progress", r.getProgress(),
                        "compileSuccess", r.getCompileSuccess() != null ? r.getCompileSuccess() : false,
                        "testSuccess", r.getTestSuccess() != null ? r.getTestSuccess() : false,
                        "outputFile", r.getOutputFilePath() != null ? r.getOutputFilePath() : "",
                        "completedAt", (Object)(r.getCompletedAt() != null ? r.getCompletedAt().toString() : ""),
                        "errorMessage", (Object)(r.getErrorMessage() != null ? r.getErrorMessage() : "")
                ))
                .map(m -> (Map<String, Object>) m)
                .toList();
    }

    /**
     * 완료된 파일 다운로드
     */
    public byte[] getConvertedFile(String jobId, String fileName) {
        com.c2java.domain.FileConversionResult fileResult = fileResultRepository
                .findByJobIdAndSourceFileName(UUID.fromString(jobId), fileName);
        
        if (fileResult == null || fileResult.getOutputFilePath() == null) {
            throw new IllegalArgumentException("변환된 파일을 찾을 수 없습니다: " + fileName);
        }
        
        try {
            Path outputPath = Paths.get(fileResult.getOutputFilePath());
            return Files.readAllBytes(outputPath);
        } catch (IOException e) {
            log.error("파일 읽기 실패: {}", fileName, e);
            throw new RuntimeException("파일 다운로드 실패: " + e.getMessage());
        }
    }

    /**
     * 전체 작업 완료 처리
     */
    @Transactional
    public Map<String, Object> finalizeJob(String jobId) {
        ConversionJob job = jobRepository.findByJobId(UUID.fromString(jobId))
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));
        
        // 모든 파일 결과 확인
        List<com.c2java.domain.FileConversionResult> fileResults = fileResultRepository
                .findByJobIdOrderByCreatedAt(job.getJobId());
        
        long completedCount = fileResults.stream()
                .filter(r -> r.getStatus() == com.c2java.domain.FileConversionResult.FileStatus.COMPLETED)
                .count();
        
        long failedCount = fileResults.stream()
                .filter(r -> r.getStatus() == com.c2java.domain.FileConversionResult.FileStatus.FAILED)
                .count();
        
        if (failedCount > 0) {
            job.setStatus(ConversionJob.JobStatus.FAILED);
            job.appendLog(String.format("❌ 일부 파일 변환 실패: %d개 성공, %d개 실패", 
                                       completedCount, failedCount));
        } else {
            job.setStatus(ConversionJob.JobStatus.COMPLETED);
            job.updateProgress("COMPLETE", 100);
            job.appendLog(String.format("🎉 전체 변환 완료: %d개 파일 성공!", completedCount));
        }
        
        jobRepository.save(job);
        
        return Map.of(
                "status", "success",
                "completedFiles", completedCount,
                "failedFiles", failedCount,
                "totalFiles", fileResults.size()
        );
    }
}

package com.c2java.service;

import com.c2java.domain.ConversionJob;
import com.c2java.domain.RuntimeResult;
import com.c2java.repository.RuntimeResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.PumpStreamHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;

/**
 * 런타임 테스트 서비스
 * 변환된 Java 코드의 런타임 테스트를 수행합니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RuntimeTestService {

    private final RuntimeResultRepository runtimeResultRepository;

    @Value("${conversion.runtime.timeout-seconds:60}")
    private int timeoutSeconds;

    /**
     * 런타임 테스트 수행
     */
    public void runTests(ConversionJob job, String outputPath) {
        log.info("Starting runtime tests for job: {}", job.getId());
        
        long startTime = System.currentTimeMillis();
        
        try {
            // Gradle 테스트 실행
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ByteArrayOutputStream errorStream = new ByteArrayOutputStream();
            
            CommandLine cmdLine = new CommandLine("gradle");
            cmdLine.addArgument("test");
            
            DefaultExecutor executor = DefaultExecutor.builder()
                    .setWorkingDirectory(Path.of(outputPath).toFile())
                    .get();
            
            ExecuteWatchdog watchdog = ExecuteWatchdog.builder()
                    .setTimeout(Duration.ofSeconds(timeoutSeconds))
                    .get();
            executor.setWatchdog(watchdog);
            
            PumpStreamHandler streamHandler = new PumpStreamHandler(outputStream, errorStream);
            executor.setStreamHandler(streamHandler);
            
            int exitCode = executor.execute(cmdLine);
            long executionTime = System.currentTimeMillis() - startTime;
            
            boolean success = exitCode == 0;
            String errors = errorStream.toString(StandardCharsets.UTF_8);
            
            RuntimeResult result = RuntimeResult.builder()
                    .job(job)
                    .testName("gradle-test")
                    .success(success)
                    .executionTimeMs((int) executionTime)
                    .errorOutput(success ? null : errors)
                    .build();
            
            runtimeResultRepository.save(result);
            
            log.info("Runtime tests completed for job: {} - success: {}", job.getId(), success);
            
        } catch (Exception e) {
            log.error("Runtime tests failed for job: {}", job.getId(), e);
            
            RuntimeResult result = RuntimeResult.builder()
                    .job(job)
                    .testName("gradle-test")
                    .success(false)
                    .executionTimeMs((int) (System.currentTimeMillis() - startTime))
                    .errorOutput(e.getMessage())
                    .build();
            
            runtimeResultRepository.save(result);
        }
    }
}

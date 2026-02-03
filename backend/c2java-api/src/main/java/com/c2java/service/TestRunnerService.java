package com.c2java.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 런타임 테스트 실행 서비스
 * 생성된 Java 프로젝트의 테스트 실행
 */
@Service
@Slf4j
public class TestRunnerService {

    private static final Pattern TEST_RESULT_PATTERN = Pattern.compile(
            "Tests run: (\\d+), Failures: (\\d+), Errors: (\\d+), Skipped: (\\d+)"
    );

    /**
     * JUnit 테스트 실행
     */
    public Map<String, Object> runTests(Path projectPath) {
        log.info("Running tests for project: {}", projectPath);
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "./gradlew", "test", "--no-daemon"
            );
            pb.directory(projectPath.toFile());
            pb.redirectErrorStream(true);
            
            Process process = pb.start();
            
            StringBuilder output = new StringBuilder();
            List<String> failedTests = new ArrayList<>();
            int testsRun = 0;
            int failures = 0;
            int errors = 0;
            int skipped = 0;
            
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    
                    // 테스트 결과 파싱
                    Matcher matcher = TEST_RESULT_PATTERN.matcher(line);
                    if (matcher.find()) {
                        testsRun += Integer.parseInt(matcher.group(1));
                        failures += Integer.parseInt(matcher.group(2));
                        errors += Integer.parseInt(matcher.group(3));
                        skipped += Integer.parseInt(matcher.group(4));
                    }
                    
                    // 실패한 테스트 추출
                    if (line.contains("FAILED")) {
                        failedTests.add(line.trim());
                    }
                }
            }
            
            int exitCode = process.waitFor();
            boolean success = exitCode == 0 && failures == 0 && errors == 0;
            int passed = testsRun - failures - errors - skipped;
            
            result.put("success", success);
            result.put("total", testsRun);
            result.put("passed", passed);
            result.put("failed", failures);
            result.put("errors", errors);
            result.put("skipped", skipped);
            result.put("failedTests", failedTests);
            result.put("output", output.toString());
            result.put("exitCode", exitCode);
            
            if (success) {
                log.info("All tests passed: {}/{}", passed, testsRun);
            } else {
                log.warn("Tests failed: {} failures, {} errors out of {} tests", 
                        failures, errors, testsRun);
            }
            
        } catch (IOException | InterruptedException e) {
            log.error("Test execution failed", e);
            result.put("success", false);
            result.put("errors", 1);
            result.put("failedTests", List.of("Test execution failed: " + e.getMessage()));
        }
        
        return result;
    }

    /**
     * 특정 테스트 클래스만 실행
     */
    public Map<String, Object> runTestClass(Path projectPath, String testClassName) {
        log.info("Running test class: {}", testClassName);
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "./gradlew", "test", "--tests", testClassName
            );
            pb.directory(projectPath.toFile());
            pb.redirectErrorStream(true);
            
            Process process = pb.start();
            
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            
            int exitCode = process.waitFor();
            
            result.put("success", exitCode == 0);
            result.put("output", output.toString());
            result.put("exitCode", exitCode);
            
        } catch (IOException | InterruptedException e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        
        return result;
    }

    /**
     * 테스트 커버리지 분석 (JaCoCo)
     */
    public Map<String, Object> analyzeCoverage(Path projectPath) {
        log.info("Analyzing test coverage: {}", projectPath);
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "./gradlew", "test", "jacocoTestReport"
            );
            pb.directory(projectPath.toFile());
            pb.redirectErrorStream(true);
            
            Process process = pb.start();
            process.waitFor();
            
            // JaCoCo 리포트 읽기 (간단 버전)
            result.put("success", process.exitValue() == 0);
            result.put("reportPath", "build/reports/jacoco/test/html/index.html");
            
        } catch (IOException | InterruptedException e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        
        return result;
    }
}

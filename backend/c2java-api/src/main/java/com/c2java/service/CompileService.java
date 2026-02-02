package com.c2java.service;

import com.c2java.domain.CompileResult;
import com.c2java.domain.ConversionJob;
import com.c2java.repository.CompileResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.PumpStreamHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 컴파일 서비스
 * 변환된 Java 코드의 컴파일 테스트를 수행합니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CompileService {

    private final CompileResultRepository compileResultRepository;
    private final CliService cliService;

    @Value("${conversion.compile.max-retries:3}")
    private int maxRetries;

    @Value("${conversion.compile.timeout-seconds:300}")
    private int timeoutSeconds;

    /**
     * 컴파일 및 테스트 수행
     */
    public boolean compileAndTest(ConversionJob job, String outputPath) {
        log.info("Starting compile test for job: {} at path: {}", job.getId(), outputPath);
        
        int attemptNumber = job.getCompileAttempts() + 1;
        
        try {
            // Gradle 프로젝트 구조 생성 (없는 경우)
            ensureGradleStructure(outputPath);
            
            // 컴파일 실행
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ByteArrayOutputStream errorStream = new ByteArrayOutputStream();
            
            CommandLine cmdLine = new CommandLine("gradle");
            cmdLine.addArgument("build");
            cmdLine.addArgument("-x");
            cmdLine.addArgument("test");  // 테스트는 별도 단계에서
            
            DefaultExecutor executor = DefaultExecutor.builder()
                    .setWorkingDirectory(Path.of(outputPath).toFile())
                    .get();
            PumpStreamHandler streamHandler = new PumpStreamHandler(outputStream, errorStream);
            executor.setStreamHandler(streamHandler);
            
            int exitCode = executor.execute(cmdLine);
            
            String output = outputStream.toString(StandardCharsets.UTF_8);
            String errors = errorStream.toString(StandardCharsets.UTF_8);
            
            boolean success = exitCode == 0;
            
            // 결과 저장
            CompileResult result = CompileResult.builder()
                    .job(job)
                    .attemptNumber(attemptNumber)
                    .success(success)
                    .errorOutput(success ? null : errors)
                    .warningCount(countWarnings(output))
                    .errorCount(countErrors(errors))
                    .build();
            
            compileResultRepository.save(result);
            
            // 컴파일 실패 시 AIDER로 수정 시도
            if (!success && job.getCompileAttempts() < maxRetries) {
                log.warn("Compile failed, attempting fix with Aider");
                try {
                    cliService.fixCompileErrors(outputPath + "/src/main/java", errors);
                } catch (IOException e) {
                    log.error("Failed to fix compile errors with Aider", e);
                }
            }
            
            return success;
            
        } catch (Exception e) {
            log.error("Compile test failed for job: {}", job.getId(), e);
            
            CompileResult result = CompileResult.builder()
                    .job(job)
                    .attemptNumber(attemptNumber)
                    .success(false)
                    .errorOutput(e.getMessage())
                    .errorCount(1)
                    .build();
            
            compileResultRepository.save(result);
            return false;
        }
    }

    /**
     * Gradle 프로젝트 구조 확인 및 생성
     */
    private void ensureGradleStructure(String outputPath) throws IOException {
        Path projectPath = Path.of(outputPath);
        Path buildGradle = projectPath.resolve("build.gradle");
        
        if (!Files.exists(buildGradle)) {
            // build.gradle 생성
            String buildGradleContent = """
                plugins {
                    id 'java'
                    id 'org.springframework.boot' version '3.2.5'
                    id 'io.spring.dependency-management' version '1.1.4'
                }
                
                group = 'com.company.converted'
                version = '1.0.0'
                
                java {
                    sourceCompatibility = '21'
                }
                
                repositories {
                    mavenCentral()
                }
                
                dependencies {
                    implementation 'org.springframework.boot:spring-boot-starter-web'
                    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
                    compileOnly 'org.projectlombok:lombok'
                    annotationProcessor 'org.projectlombok:lombok'
                    testImplementation 'org.springframework.boot:spring-boot-starter-test'
                }
                
                tasks.named('test') {
                    useJUnitPlatform()
                }
                """;
            
            Files.writeString(buildGradle, buildGradleContent);
        }
        
        // src 디렉토리 구조 생성
        Path srcMainJava = projectPath.resolve("src/main/java/com/company/converted");
        Files.createDirectories(srcMainJava);
        
        Path srcMainResources = projectPath.resolve("src/main/resources");
        Files.createDirectories(srcMainResources);
    }

    /**
     * 경고 수 카운트
     */
    private int countWarnings(String output) {
        int count = 0;
        for (String line : output.split("\n")) {
            if (line.toLowerCase().contains("warning")) {
                count++;
            }
        }
        return count;
    }

    /**
     * 에러 수 카운트
     */
    private int countErrors(String output) {
        int count = 0;
        for (String line : output.split("\n")) {
            if (line.toLowerCase().contains("error")) {
                count++;
            }
        }
        return count;
    }
}

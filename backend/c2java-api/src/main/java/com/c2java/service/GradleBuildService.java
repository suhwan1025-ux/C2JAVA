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

/**
 * Gradle 빌드 검증 서비스
 * 생성된 Java 프로젝트를 컴파일하여 검증
 */
@Service
@Slf4j
public class GradleBuildService {

    /**
     * Gradle 빌드 실행
     */
    public Map<String, Object> buildProject(Path projectPath) {
        log.info("Building project: {}", projectPath);
        
        Map<String, Object> result = new HashMap<>();
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        try {
            // Gradle clean build 실행
            ProcessBuilder pb = new ProcessBuilder(
                    "./gradlew", "clean", "build", "-x", "test"
            );
            pb.directory(projectPath.toFile());
            pb.redirectErrorStream(true);
            
            Process process = pb.start();
            
            // 출력 읽기
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    
                    // 에러 및 경고 추출
                    if (line.contains("error:") || line.contains("ERROR")) {
                        errors.add(line);
                    } else if (line.contains("warning:") || line.contains("WARN")) {
                        warnings.add(line);
                    }
                }
            }
            
            int exitCode = process.waitFor();
            boolean success = exitCode == 0 && errors.isEmpty();
            
            result.put("success", success);
            result.put("exitCode", exitCode);
            result.put("output", output.toString());
            result.put("errors", errors);
            result.put("warnings", warnings);
            result.put("errorCount", errors.size());
            result.put("warningCount", warnings.size());
            
            if (success) {
                log.info("Build successful");
            } else {
                log.warn("Build failed with {} errors", errors.size());
            }
            
        } catch (IOException | InterruptedException e) {
            log.error("Build process failed", e);
            result.put("success", false);
            result.put("errors", List.of("Build process failed: " + e.getMessage()));
            result.put("errorCount", 1);
        }
        
        return result;
    }

    /**
     * 컴파일만 수행 (빠른 검증)
     */
    public Map<String, Object> compileOnly(Path projectPath) {
        log.info("Compiling project: {}", projectPath);
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "./gradlew", "compileJava"
            );
            pb.directory(projectPath.toFile());
            pb.redirectErrorStream(true);
            
            Process process = pb.start();
            
            StringBuilder output = new StringBuilder();
            List<String> errors = new ArrayList<>();
            
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    if (line.contains("error:")) {
                        errors.add(line);
                    }
                }
            }
            
            int exitCode = process.waitFor();
            
            result.put("success", exitCode == 0);
            result.put("exitCode", exitCode);
            result.put("output", output.toString());
            result.put("errors", errors);
            result.put("errorCount", errors.size());
            
        } catch (IOException | InterruptedException e) {
            result.put("success", false);
            result.put("errors", List.of(e.getMessage()));
        }
        
        return result;
    }

    /**
     * 빌드 파일 생성 (build.gradle)
     */
    public String generateBuildGradle(String groupId, String artifactId, String version) {
        return String.format("""
                plugins {
                    id 'java'
                    id 'org.springframework.boot' version '3.2.5'
                    id 'io.spring.dependency-management' version '1.1.4'
                }
                
                group = '%s'
                version = '%s'
                
                java {
                    sourceCompatibility = '21'
                }
                
                configurations {
                    compileOnly {
                        extendsFrom annotationProcessor
                    }
                }
                
                repositories {
                    mavenCentral()
                }
                
                dependencies {
                    implementation 'org.springframework.boot:spring-boot-starter-web'
                    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
                    implementation 'org.springframework.boot:spring-boot-starter-validation'
                    
                    runtimeOnly 'org.postgresql:postgresql'
                    runtimeOnly 'com.h2database:h2'
                    
                    compileOnly 'org.projectlombok:lombok'
                    annotationProcessor 'org.projectlombok:lombok'
                    annotationProcessor 'org.mapstruct:mapstruct-processor:1.5.5.Final'
                    implementation 'org.mapstruct:mapstruct:1.5.5.Final'
                    
                    testImplementation 'org.springframework.boot:spring-boot-starter-test'
                }
                
                tasks.named('test') {
                    useJUnitPlatform()
                }
                """, groupId, version);
    }
}

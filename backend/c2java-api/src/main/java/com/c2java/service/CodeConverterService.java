package com.c2java.service;

import com.c2java.domain.ConversionJob;
import com.c2java.dto.CFileStructure;
import com.c2java.dto.CFileStructure.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 코드 변환 엔진
 * 분석 결과 + 규칙 + LLM을 조합하여 Java 코드 생성
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CodeConverterService {

    private final CFileAnalyzerService analyzerService;
    private final RulesService rulesService;
    private final LlmService llmService;
    private final CliService cliService;
    private final EnvSyncService envSyncService;
    private final ObjectMapper objectMapper;

    /**
     * C 파일을 Java로 변환
     */
    public Map<String, String> convertCFiles(ConversionJob job, List<Path> sourceFiles) throws IOException {
        log.info("Starting conversion for job: {}", job.getJobId());
        
        // 1. 규칙 파일 로드
        Map<String, Object> languageRules = rulesService.getLanguageDetail(job.getTargetLanguage());
        String conversionRules = (String) languageRules.get("conversionRules");
        String projectStructure = (String) languageRules.get("projectStructure");
        
        // 2. 각 파일 분석
        List<CFileStructure> analyzed = new ArrayList<>();
        for (Path file : sourceFiles) {
            CFileStructure structure = analyzerService.analyzeFile(file);
            analyzed.add(structure);
            log.info("Analyzed {}: {} functions, {} structs, {} SQL queries",
                    structure.getFileName(),
                    structure.getFunctions().size(),
                    structure.getStructs().size(),
                    structure.getSqlQueries().size());
        }
        
        // 3. 파일별 변환
        Map<String, String> generatedFiles = new LinkedHashMap<>();
        int totalFiles = analyzed.size();
        int currentFile = 0;
        
        for (CFileStructure structure : analyzed) {
            currentFile++;
            log.info("Converting file {}/{}: {}", currentFile, totalFiles, structure.getFileName());
            
            // 파일 타입별 변환 전략
            Map<String, String> converted = convertByFileType(
                    structure, 
                    conversionRules, 
                    projectStructure,
                    sourceFiles.stream()
                            .filter(p -> p.getFileName().toString().equals(structure.getFileName()))
                            .findFirst()
                            .orElse(null)
            );
            
            generatedFiles.putAll(converted);
        }
        
        // 4. Gradle 프로젝트 파일 생성 (build.gradle, settings.gradle, application.yml)
        String projectName = "converted-project-" + job.getJobId().toString().substring(0, 8);
        generatedFiles.putAll(generateGradleProjectFiles(job.getTargetLanguage(), projectName));
        
        // 5. 매핑 파일 생성
        String mappingFile = generateMappingFile(analyzed, generatedFiles);
        generatedFiles.put("conversion-mapping.json", mappingFile);
        
        log.info("Conversion completed. Generated {} files", generatedFiles.size());
        return generatedFiles;
    }

    /**
     * 환경변수에 따라 CLI 또는 LLM 사용
     */
    private String convertWithActiveBackend(String sourceCode, String sourcePath, 
                                            String conversionRules, String projectStructure, String prompt) {
        try {
            Map<String, String> cliConfig = envSyncService.loadCliEnvVariables();
            String activeTool = cliConfig.getOrDefault("ACTIVE_CLI_TOOL", "aider");
            
            log.info("Active conversion tool: {}", activeTool);
            
            // 외부망 CLI 도구 (cursor, claude)는 CLI Service 사용
            if ("cursor".equals(activeTool) || "claude".equals(activeTool)) {
                log.info("Using CLI Service for conversion with {}", activeTool);
                String result = cliService.convertWithActiveCli(sourcePath, sourceCode, conversionRules, prompt);
                if (result != null && !result.isEmpty()) {
                    return result;
                }
                log.warn("CLI conversion returned empty, falling back to direct LLM API");
            }
            
            // 폐쇄망 (aider, fabric) 또는 fallback: 직접 LLM API 호출
            log.info("Using direct LLM API for conversion");
            return llmService.convertCode(sourceCode, conversionRules, projectStructure, prompt);
            
        } catch (Exception e) {
            log.error("Conversion backend selection failed, using LLM API as fallback", e);
            return llmService.convertCode(sourceCode, conversionRules, projectStructure, prompt);
        }
    }

    /**
     * 파일 타입별 변환
     */
    private Map<String, String> convertByFileType(CFileStructure structure, 
                                                   String conversionRules,
                                                   String projectStructure,
                                                   Path sourcePath) throws IOException {
        Map<String, String> generated = new LinkedHashMap<>();
        String sourceCode = sourcePath != null ? Files.readString(sourcePath) : "";
        
        switch (structure.getFileType()) {
            case "pro_c" -> generated.putAll(convertProCFile(structure, sourceCode, sourcePath != null ? sourcePath.toString() : "", conversionRules, projectStructure));
            case "c_source" -> generated.putAll(convertCSourceFile(structure, sourceCode, sourcePath != null ? sourcePath.toString() : "", conversionRules, projectStructure));
            case "c_header" -> generated.putAll(convertCHeaderFile(structure, sourceCode, sourcePath != null ? sourcePath.toString() : "", conversionRules, projectStructure));
        }
        
        return generated;
    }

    /**
     * Pro*C 파일 변환 → Repository + Entity
     */
    private Map<String, String> convertProCFile(CFileStructure structure, String sourceCode, String sourcePath,
                                                String conversionRules, String projectStructure) {
        Map<String, String> generated = new LinkedHashMap<>();
        String baseName = structure.getFileName().replace(".pc", "");
        
        // Repository 생성
        String repositoryPrompt = String.format("""
                다음 Pro*C 파일을 Spring Data JPA Repository로 변환하세요.
                
                [분석 정보]
                - 함수 수: %d
                - SQL 쿼리 수: %d
                - 구조체 수: %d
                
                [중요]
                - SQL은 100%% 원본 그대로 보존
                - 바인드 변수만 camelCase로 변경
                - [C2JAVA-SQL] 주석 필수
                
                [라이브러리 제약]
                - Spring Data JPA (JpaRepository, @Query, @Param)만 사용
                - import는 org.springframework.*, javax.persistence.*, lombok.* 만
                - 절대 금지: Hibernate 직접 호출, JDBC Template, MyBatis
                
                Repository 인터페이스 코드만 생성하세요.
                파일명: %sRepository.java
                """,
                structure.getFunctions().size(),
                structure.getSqlQueries().size(),
                structure.getStructs().size(),
                baseName);
        
        String repositoryCode = convertWithActiveBackend(sourceCode, sourcePath, conversionRules, projectStructure, repositoryPrompt);
        generated.put(baseName + "Repository.java", repositoryCode);
        
        // Entity 생성 (구조체가 있는 경우)
        if (!structure.getStructs().isEmpty()) {
            String entityPrompt = String.format("""
                    다음 구조체를 JPA Entity로 변환하세요.
                    
                    [구조체 정보]
                    %s
                    
                    Entity 클래스 코드만 생성하세요.
                    파일명: %s.java
                    """,
                    structure.getStructs().stream()
                            .map(s -> s.getName() + ": " + s.getFields().size() + " fields")
                            .reduce((a, b) -> a + ", " + b).orElse(""),
                    baseName);
            
            String entityCode = convertWithActiveBackend(sourceCode, sourcePath, conversionRules, projectStructure, entityPrompt);
            generated.put(baseName + ".java", entityCode);
        }
        
        return generated;
    }

    /**
     * C 소스 파일 변환 → Service + Controller
     */
    private Map<String, String> convertCSourceFile(CFileStructure structure, String sourceCode, String sourcePath,
                                                   String conversionRules, String projectStructure) {
        Map<String, String> generated = new LinkedHashMap<>();
        String baseName = structure.getFileName().replace(".c", "");
        
        // Service 인터페이스
        String servicePrompt = String.format("""
                다음 C 파일의 함수들을 Service 인터페이스로 변환하세요.
                
                [함수 목록]
                %s
                
                [라이브러리 제약]
                - Java 표준 라이브러리 (java.util.*, java.time.*, java.math.*)만 사용
                - Spring Framework (org.springframework.*)는 ServiceImpl에서만
                - 절대 금지: 외부 HTTP 클라이언트, AWS SDK, Azure SDK
                
                Service 인터페이스 코드만 생성하세요.
                파일명: %sService.java
                """,
                structure.getFunctions().stream()
                        .map(f -> f.getName() + "(" + f.getParameters().size() + " params)")
                        .reduce((a, b) -> a + ", " + b).orElse(""),
                baseName);
        
        String serviceCode = convertWithActiveBackend(sourceCode, sourcePath, conversionRules, projectStructure, servicePrompt);
        generated.put(baseName + "Service.java", serviceCode);
        
        // ServiceImpl
        String implPrompt = String.format("""
                Service 인터페이스의 구현체를 생성하세요.
                원본 C 함수 로직을 Java로 변환하세요.
                
                [라이브러리 제약]
                - Spring Boot: @Service, @Transactional, @RequiredArgsConstructor (lombok)
                - 로깅: @Slf4j (lombok) 또는 System.out
                - 유틸: Apache Commons Lang3 (StringUtils, NumberUtils)
                - 컬렉션: java.util.*, java.util.stream.*, Guava (Lists, Maps)
                - 절대 금지: RestTemplate 직접 생성, 외부 API 직접 호출
                
                ServiceImpl 클래스 코드만 생성하세요.
                파일명: %sServiceImpl.java
                """, baseName);
        
        String implCode = convertWithActiveBackend(sourceCode, sourcePath, conversionRules, projectStructure, implPrompt);
        generated.put(baseName + "ServiceImpl.java", implCode);
        
        return generated;
    }

    /**
     * C 헤더 파일 변환 → Entity + DTO + Enum
     */
    private Map<String, String> convertCHeaderFile(CFileStructure structure, String sourceCode, String sourcePath,
                                                   String conversionRules, String projectStructure) {
        Map<String, String> generated = new LinkedHashMap<>();
        String baseName = structure.getFileName().replace(".h", "");
        
        // 구조체 → Entity
        for (StructInfo struct : structure.getStructs()) {
            String entityPrompt = String.format("""
                    다음 구조체를 JPA Entity로 변환하세요.
                    
                    구조체명: %s
                    필드 수: %d
                    
                    Entity 클래스 코드만 생성하세요.
                    파일명: %s.java
                    """,
                    struct.getName(),
                    struct.getFields().size(),
                    struct.getName());
            
            String entityCode = convertWithActiveBackend(sourceCode, sourcePath, conversionRules, projectStructure, entityPrompt);
            generated.put(struct.getName() + ".java", entityCode);
        }
        
        // Enum 변환
        for (EnumInfo enumInfo : structure.getEnums()) {
            String enumPrompt = String.format("""
                    다음 enum을 Java enum으로 변환하세요.
                    
                    Enum명: %s
                    값 수: %d
                    
                    Enum 코드만 생성하세요.
                    파일명: %s.java
                    """,
                    enumInfo.getName(),
                    enumInfo.getValues().size(),
                    enumInfo.getName());
            
            String enumCode = convertWithActiveBackend(sourceCode, sourcePath, conversionRules, projectStructure, enumPrompt);
            generated.put(enumInfo.getName() + ".java", enumCode);
        }
        
        // #define 상수 → Constants 클래스
        if (!structure.getDefines().isEmpty()) {
            String constantsPrompt = String.format("""
                    다음 #define 매크로들을 Java 상수 클래스로 변환하세요.
                    
                    매크로 수: %d
                    
                    Constants 클래스 코드만 생성하세요.
                    파일명: %sConstants.java
                    """,
                    structure.getDefines().size(),
                    baseName);
            
            String constantsCode = convertWithActiveBackend(sourceCode, sourcePath, conversionRules, projectStructure, constantsPrompt);
            generated.put(baseName + "Constants.java", constantsCode);
        }
        
        return generated;
    }

    /**
     * 매핑 파일 생성 (추적성)
     */
    private String generateMappingFile(List<CFileStructure> analyzed, Map<String, String> generated) {
        try {
            Map<String, Object> mapping = new LinkedHashMap<>();
            mapping.put("version", "1.0");
            mapping.put("conversion_date", LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));
            mapping.put("rule_version", "springboot-3.2.5");
            
            // 통계
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("total_source_files", analyzed.size());
            summary.put("total_target_files", generated.size());
            summary.put("total_functions", analyzed.stream().mapToInt(a -> a.getFunctions().size()).sum());
            summary.put("total_structs", analyzed.stream().mapToInt(a -> a.getStructs().size()).sum());
            summary.put("total_sql_queries", analyzed.stream().mapToInt(a -> a.getSqlQueries().size()).sum());
            mapping.put("summary", summary);
            
            // 소스 파일 목록
            List<Map<String, Object>> sourceFiles = analyzed.stream()
                    .map(a -> {
                        Map<String, Object> src = new LinkedHashMap<>();
                        src.put("path", a.getFileName());
                        src.put("type", a.getFileType());
                        src.put("functions", a.getFunctions().size());
                        src.put("structs", a.getStructs().size());
                        return src;
                    })
                    .toList();
            mapping.put("source_files", sourceFiles);
            
            // 생성 파일 목록
            List<Map<String, String>> targetFiles = generated.keySet().stream()
                    .map(fileName -> {
                        Map<String, String> tgt = new LinkedHashMap<>();
                        tgt.put("file", fileName);
                        tgt.put("type", determineJavaFileType(fileName));
                        return tgt;
                    })
                    .toList();
            mapping.put("target_files", targetFiles);
            
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(mapping);
        } catch (Exception e) {
            log.error("Failed to generate mapping file", e);
            return "{}";
        }
    }

    /**
     * Java 파일 타입 결정
     */
    private String determineJavaFileType(String fileName) {
        if (fileName.endsWith("Repository.java")) return "repository";
        if (fileName.endsWith("Service.java")) return "service";
        if (fileName.endsWith("ServiceImpl.java")) return "service_impl";
        if (fileName.endsWith("Controller.java")) return "controller";
        if (fileName.endsWith("Request.java")) return "dto_request";
        if (fileName.endsWith("Response.java")) return "dto_response";
        if (fileName.endsWith("Dto.java")) return "dto";
        if (fileName.endsWith("Constants.java")) return "constants";
        if (fileName.contains("domain/")) return "entity";
        return "class";
    }

    /**
     * Gradle 프로젝트 파일 생성
     */
    private Map<String, String> generateGradleProjectFiles(String targetLanguage, String projectName) {
        Map<String, String> projectFiles = new LinkedHashMap<>();
        
        try {
            // 규칙 파일 경로
            Path rulesDir = Paths.get(System.getProperty("user.home"), "Desktop", "C2JAVA", "config", "rules", targetLanguage);
            
            // build.gradle 템플릿 로드
            Path buildGradleTemplate = rulesDir.resolve("build.gradle.template");
            if (Files.exists(buildGradleTemplate)) {
                String buildGradle = Files.readString(buildGradleTemplate);
                buildGradle = buildGradle.replace("{{PROJECT_NAME}}", projectName);
                projectFiles.put("build.gradle", buildGradle);
                log.info("Generated build.gradle");
            } else {
                log.warn("build.gradle.template not found, using default");
                projectFiles.put("build.gradle", generateDefaultBuildGradle());
            }
            
            // settings.gradle 템플릿 로드
            Path settingsGradleTemplate = rulesDir.resolve("settings.gradle.template");
            if (Files.exists(settingsGradleTemplate)) {
                String settingsGradle = Files.readString(settingsGradleTemplate);
                settingsGradle = settingsGradle.replace("{{PROJECT_NAME}}", projectName);
                projectFiles.put("settings.gradle", settingsGradle);
                log.info("Generated settings.gradle");
            } else {
                projectFiles.put("settings.gradle", "rootProject.name = '" + projectName + "'");
            }
            
            // application.yml 템플릿 로드
            Path applicationYmlTemplate = rulesDir.resolve("application.yml.template");
            if (Files.exists(applicationYmlTemplate)) {
                String applicationYml = Files.readString(applicationYmlTemplate);
                applicationYml = applicationYml.replace("{{PROJECT_NAME}}", projectName);
                projectFiles.put("src/main/resources/application.yml", applicationYml);
                log.info("Generated application.yml");
            }
            
            // gradlew, gradlew.bat (실행 가능 wrapper 스크립트)
            projectFiles.put("gradlew", generateGradlew());
            projectFiles.put("gradlew.bat", generateGradlewBat());
            projectFiles.put("gradle/wrapper/gradle-wrapper.properties", generateGradleWrapperProperties());
            
            log.info("Generated {} Gradle project files", projectFiles.size());
            
        } catch (IOException e) {
            log.error("Failed to generate Gradle project files", e);
            projectFiles.put("build.gradle", generateDefaultBuildGradle());
            projectFiles.put("settings.gradle", "rootProject.name = '" + projectName + "'");
        }
        
        return projectFiles;
    }

    /**
     * 기본 build.gradle 생성
     */
    private String generateDefaultBuildGradle() {
        return """
                plugins {
                    id 'java'
                    id 'org.springframework.boot' version '3.2.5'
                    id 'io.spring.dependency-management' version '1.1.4'
                }
                
                group = 'com.converted'
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
                    runtimeOnly 'org.postgresql:postgresql'
                    compileOnly 'org.projectlombok:lombok'
                    annotationProcessor 'org.projectlombok:lombok'
                }
                """;
    }

    /**
     * gradlew 스크립트 생성
     */
    private String generateGradlew() {
        return """
                #!/bin/sh
                # Gradle Wrapper Script
                exec gradle "$@"
                """;
    }

    /**
     * gradlew.bat 스크립트 생성
     */
    private String generateGradlewBat() {
        return """
                @echo off
                gradle %*
                """;
    }

    /**
     * gradle-wrapper.properties 생성
     */
    private String generateGradleWrapperProperties() {
        return """
                distributionBase=GRADLE_USER_HOME
                distributionPath=wrapper/dists
                distributionUrl=https\\://services.gradle.org/distributions/gradle-8.5-bin.zip
                zipStoreBase=GRADLE_USER_HOME
                zipStorePath=wrapper/dists
                """;
    }
}

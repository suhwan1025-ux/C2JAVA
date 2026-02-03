package com.c2java.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 변환 규칙 관리 서비스
 * 언어별 C to Java 변환 규칙 파일을 관리합니다.
 * 
 * 폴더 구조:
 * config/rules/
 * ├── springboot-3.2.5/
 * │   ├── conversion-rules.yaml
 * │   └── project-structure.yaml
 * ├── nodejs-18/
 * │   ├── conversion-rules.yaml
 * │   └── project-structure.yaml
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RulesService {

    @Value("${conversion.rules-dir:config/rules}")
    private String rulesDir;

    private static final String CONVERSION_RULES_FILE = "conversion-rules.yaml";
    private static final String PROJECT_STRUCTURE_FILE = "project-structure.yaml";

    /**
     * 등록된 언어 목록 조회
     */
    public List<Map<String, Object>> listLanguages() throws IOException {
        Path rulesPath = resolveRulesPath();
        
        if (!Files.exists(rulesPath)) {
            Files.createDirectories(rulesPath);
            return Collections.emptyList();
        }

        try (Stream<Path> paths = Files.list(rulesPath)) {
            return paths
                    .filter(Files::isDirectory)
                    .map(this::languageToMap)
                    .sorted((a, b) -> ((String) a.get("name")).compareTo((String) b.get("name")))
                    .collect(Collectors.toList());
        }
    }

    /**
     * 언어 상세 정보 조회 (파일 포함)
     */
    public Map<String, Object> getLanguageDetail(String languageName) throws IOException {
        Path langPath = resolveRulesPath().resolve(languageName);
        
        if (!Files.exists(langPath) || !Files.isDirectory(langPath)) {
            throw new IllegalArgumentException("Language not found: " + languageName);
        }

        Map<String, Object> result = languageToMap(langPath);
        
        // 변환 규칙 파일 내용
        Path conversionFile = langPath.resolve(CONVERSION_RULES_FILE);
        if (Files.exists(conversionFile)) {
            result.put("conversionRules", Files.readString(conversionFile));
        }
        
        // 프로젝트 구조 파일 내용
        Path structureFile = langPath.resolve(PROJECT_STRUCTURE_FILE);
        if (Files.exists(structureFile)) {
            result.put("projectStructure", Files.readString(structureFile));
        }

        return result;
    }

    /**
     * 새 언어 생성
     */
    public Map<String, Object> createLanguage(String languageName) throws IOException {
        // 이름 유효성 검사
        if (!isValidLanguageName(languageName)) {
            throw new IllegalArgumentException("Invalid language name. Use only letters, numbers, and hyphens.");
        }

        Path langPath = resolveRulesPath().resolve(languageName);
        
        if (Files.exists(langPath)) {
            throw new IllegalArgumentException("Language already exists: " + languageName);
        }

        Files.createDirectories(langPath);
        
        // 기본 템플릿 파일 생성
        Files.writeString(langPath.resolve(CONVERSION_RULES_FILE), getDefaultConversionRules(languageName));
        Files.writeString(langPath.resolve(PROJECT_STRUCTURE_FILE), getDefaultProjectStructure(languageName));
        
        log.info("Language created: {}", languageName);
        return getLanguageDetail(languageName);
    }

    /**
     * 언어 삭제
     */
    public void deleteLanguage(String languageName) throws IOException {
        Path langPath = resolveRulesPath().resolve(languageName);
        
        if (!Files.exists(langPath)) {
            throw new IllegalArgumentException("Language not found: " + languageName);
        }

        // 백업 후 삭제
        String backupName = languageName + ".deleted." + System.currentTimeMillis();
        Files.move(langPath, resolveRulesPath().resolve(backupName));
        log.info("Language deleted (backup: {}): {}", backupName, languageName);
    }

    /**
     * 변환 규칙 파일 저장
     */
    public Map<String, Object> saveConversionRules(String languageName, String content) throws IOException {
        Path langPath = resolveRulesPath().resolve(languageName);
        
        if (!Files.exists(langPath)) {
            throw new IllegalArgumentException("Language not found: " + languageName);
        }

        Path filePath = langPath.resolve(CONVERSION_RULES_FILE);
        
        // 백업
        if (Files.exists(filePath)) {
            String backupName = CONVERSION_RULES_FILE + ".backup." + System.currentTimeMillis();
            Files.copy(filePath, langPath.resolve(backupName));
        }

        Files.writeString(filePath, content);
        log.info("Conversion rules saved for: {}", languageName);
        
        return getLanguageDetail(languageName);
    }

    /**
     * 프로젝트 구조 파일 저장
     */
    public Map<String, Object> saveProjectStructure(String languageName, String content) throws IOException {
        Path langPath = resolveRulesPath().resolve(languageName);
        
        if (!Files.exists(langPath)) {
            throw new IllegalArgumentException("Language not found: " + languageName);
        }

        Path filePath = langPath.resolve(PROJECT_STRUCTURE_FILE);
        
        // 백업
        if (Files.exists(filePath)) {
            String backupName = PROJECT_STRUCTURE_FILE + ".backup." + System.currentTimeMillis();
            Files.copy(filePath, langPath.resolve(backupName));
        }

        Files.writeString(filePath, content);
        log.info("Project structure saved for: {}", languageName);
        
        return getLanguageDetail(languageName);
    }

    /**
     * 변환 규칙 파일 업로드
     */
    public Map<String, Object> uploadConversionRules(String languageName, MultipartFile file) throws IOException {
        String content = new String(file.getBytes());
        return saveConversionRules(languageName, content);
    }

    /**
     * 프로젝트 구조 파일 업로드
     */
    public Map<String, Object> uploadProjectStructure(String languageName, MultipartFile file) throws IOException {
        String content = new String(file.getBytes());
        return saveProjectStructure(languageName, content);
    }

    /**
     * 사용자용 언어 목록 (간단한 정보만)
     */
    public List<Map<String, String>> listAvailableLanguages() throws IOException {
        Path rulesPath = resolveRulesPath();
        
        if (!Files.exists(rulesPath)) {
            return Collections.emptyList();
        }

        try (Stream<Path> paths = Files.list(rulesPath)) {
            return paths
                    .filter(Files::isDirectory)
                    .filter(path -> {
                        // 두 파일이 모두 있어야 유효한 언어
                        return Files.exists(path.resolve(CONVERSION_RULES_FILE)) &&
                               Files.exists(path.resolve(PROJECT_STRUCTURE_FILE));
                    })
                    .map(path -> {
                        Map<String, String> lang = new LinkedHashMap<>();
                        lang.put("id", path.getFileName().toString());
                        lang.put("name", formatLanguageName(path.getFileName().toString()));
                        return lang;
                    })
                    .sorted((a, b) -> a.get("name").compareTo(b.get("name")))
                    .collect(Collectors.toList());
        }
    }

    /**
     * 규칙 디렉토리 경로 확인
     */
    private Path resolveRulesPath() {
        Path path = Paths.get(rulesDir);
        if (!path.isAbsolute()) {
            path = Paths.get(System.getProperty("user.dir")).resolve(rulesDir);
        }
        return path;
    }

    /**
     * 언어명 유효성 검사
     */
    private boolean isValidLanguageName(String name) {
        return name != null && name.matches("^[a-zA-Z0-9][a-zA-Z0-9-_.]*$");
    }

    /**
     * 언어명 포맷팅 (예: springboot-3.2.5 -> Spring Boot 3.2.5)
     */
    private String formatLanguageName(String name) {
        // 특정 패턴 처리
        if (name.toLowerCase().startsWith("springboot")) {
            String version = name.substring(10).replace("-", " ").trim();
            return "Spring Boot " + version;
        }
        if (name.toLowerCase().startsWith("spring-boot")) {
            String version = name.substring(11).replace("-", " ").trim();
            return "Spring Boot " + version;
        }
        // 일반 케이스
        return name.replace("-", " ").replace("_", " ");
    }

    /**
     * 언어 폴더 정보를 Map으로 변환
     */
    private Map<String, Object> languageToMap(Path langPath) {
        Map<String, Object> map = new LinkedHashMap<>();
        String name = langPath.getFileName().toString();
        map.put("id", name);
        map.put("name", name);
        map.put("displayName", formatLanguageName(name));
        
        try {
            map.put("lastModified", formatDateTime(Files.getLastModifiedTime(langPath).toInstant()));
            
            // 파일 존재 여부
            Path conversionFile = langPath.resolve(CONVERSION_RULES_FILE);
            Path structureFile = langPath.resolve(PROJECT_STRUCTURE_FILE);
            
            map.put("hasConversionRules", Files.exists(conversionFile));
            map.put("hasProjectStructure", Files.exists(structureFile));
            
            if (Files.exists(conversionFile)) {
                map.put("conversionRulesSize", Files.size(conversionFile));
            }
            if (Files.exists(structureFile)) {
                map.put("projectStructureSize", Files.size(structureFile));
            }
            
            // 완전한 설정 여부
            map.put("isComplete", Files.exists(conversionFile) && Files.exists(structureFile));
            
        } catch (IOException e) {
            log.error("Error reading language info: {}", langPath, e);
        }
        
        return map;
    }

    private String formatDateTime(Instant instant) {
        return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(ZoneId.systemDefault())
                .format(instant);
    }

    /**
     * 기본 변환 규칙 템플릿
     */
    private String getDefaultConversionRules(String languageName) {
        return String.format("""
                # ============================================
                # %s 변환 규칙
                # ============================================
                
                version: "1.0"
                language: "%s"
                
                # 타입 매핑
                type_mapping:
                  primitives:
                    "int": "int"
                    "long": "long"
                    "float": "float"
                    "double": "double"
                    "char": "char"
                    "void": "void"
                  pointers:
                    "char*": "String"
                    "int*": "int[]"
                    "void*": "Object"
                
                # 함수 매핑
                function_mapping:
                  io:
                    "printf": 
                      java_method: "System.out.printf"
                    "scanf":
                      java_method: "Scanner.next"
                      import: "java.util.Scanner"
                  memory:
                    "malloc":
                      java_method: "new"
                      note: "Java uses garbage collection"
                    "free":
                      java_method: "// GC handles memory"
                """, formatLanguageName(languageName), languageName);
    }

    /**
     * 기본 프로젝트 구조 템플릿
     */
    private String getDefaultProjectStructure(String languageName) {
        return String.format("""
                # ============================================
                # %s 프로젝트 구조
                # ============================================
                
                version: "1.0"
                language: "%s"
                
                project_structure:
                  base_package: "com.company.converted"
                  
                  modules:
                    - name: "api"
                      description: "REST API 컨트롤러"
                      path: "src/main/java/{base_package}/api"
                      
                    - name: "service"
                      description: "비즈니스 로직"
                      path: "src/main/java/{base_package}/service"
                      
                    - name: "repository"
                      description: "데이터 접근 계층"
                      path: "src/main/java/{base_package}/repository"
                      
                    - name: "domain"
                      description: "도메인 모델"
                      path: "src/main/java/{base_package}/domain"
                      
                    - name: "dto"
                      description: "DTO"
                      path: "src/main/java/{base_package}/dto"
                      
                    - name: "config"
                      description: "설정"
                      path: "src/main/java/{base_package}/config"
                """, formatLanguageName(languageName), languageName);
    }
}

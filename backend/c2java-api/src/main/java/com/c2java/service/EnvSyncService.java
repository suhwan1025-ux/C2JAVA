package com.c2java.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import java.util.stream.Collectors;

/**
 * 환경변수 파일 동기화 서비스
 * DB 설정과 .env 파일을 동기화합니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EnvSyncService {

    @Value("${env.file.path:config/env/.env.internal}")
    private String envFilePath;

    private static final Set<String> LLM_ENV_KEYS = Set.of(
            "ACTIVE_LLM_PROVIDER",
            "QWEN3_API_URL", "QWEN3_API_KEY", "QWEN3_MODEL_NAME", "QWEN3_MAX_TOKENS", "QWEN3_TEMPERATURE",
            "GPT_OSS_API_URL", "GPT_OSS_API_KEY", "GPT_OSS_MODEL_NAME", "GPT_OSS_MAX_TOKENS", "GPT_OSS_TEMPERATURE"
    );

    private static final Set<String> CLI_ENV_KEYS = Set.of(
            "AIDER_ENABLED", "AIDER_AUTO_COMMITS",
            "FABRIC_ENABLED", "FABRIC_DEFAULT_PATTERN"
    );

    private static final Set<String> FILE_SERVER_ENV_KEYS = Set.of(
            "FILE_SERVER_ENABLED", "FILE_SERVER_URL",
            "FILE_SERVER_UPLOAD_ENDPOINT", "FILE_SERVER_AUTH_TOKEN"
    );

    private static final Set<String> WORKER_SERVER_ENV_KEYS = Set.of(
            "WORKER_SERVER_URL",
            "CLI_SERVICE_ENABLED", "CLI_SERVICE_PORT",
            "MCP_ENABLED", "MCP_SERVICE_PORT",
            "AIRFLOW_ENABLED", "AIRFLOW_PORT",
            "GRAFANA_ENABLED", "GRAFANA_PORT",
            "FILE_SERVER_ENABLED", "FILE_SERVER_PORT"
    );

    /**
     * 환경변수 파일에서 LLM 설정 읽기
     */
    public Map<String, String> loadLlmEnvVariables() {
        return loadEnvVariables(LLM_ENV_KEYS);
    }

    /**
     * 환경변수 파일에서 CLI 설정 읽기
     */
    public Map<String, String> loadCliEnvVariables() {
        return loadEnvVariables(CLI_ENV_KEYS);
    }

    /**
     * 환경변수 파일에서 파일 서버 설정 읽기
     */
    public Map<String, String> loadFileServerEnvVariables() {
        return loadEnvVariables(FILE_SERVER_ENV_KEYS);
    }

    /**
     * 환경변수 파일에서 워커 서버 설정 읽기
     */
    public Map<String, String> loadWorkerServerEnvVariables() {
        return loadEnvVariables(WORKER_SERVER_ENV_KEYS);
    }

    /**
     * 환경변수 파일에서 특정 키들의 값 읽기
     */
    public Map<String, String> loadEnvVariables(Set<String> keys) {
        Map<String, String> envVars = new LinkedHashMap<>();
        Path path = resolveEnvFilePath();

        if (!Files.exists(path)) {
            log.warn("Environment file not found: {}", path);
            return envVars;
        }

        try {
            List<String> lines = Files.readAllLines(path);
            Pattern pattern = Pattern.compile("^([A-Z_]+)=(.*)$");

            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }

                Matcher matcher = pattern.matcher(trimmed);
                if (matcher.matches()) {
                    String key = matcher.group(1);
                    String value = matcher.group(2);
                    if (keys.contains(key)) {
                        envVars.put(key, value);
                    }
                }
            }
        } catch (IOException e) {
            log.error("Failed to read environment file: {}", e.getMessage());
        }

        return envVars;
    }

    /**
     * 환경변수 파일에 값 저장
     */
    public void saveEnvVariable(String key, String value) {
        saveEnvVariables(Map.of(key, value));
    }

    /**
     * 환경변수 파일에 여러 값 저장
     */
    public void saveEnvVariables(Map<String, String> updates) {
        Path path = resolveEnvFilePath();

        if (!Files.exists(path)) {
            log.warn("Environment file not found, cannot save: {}", path);
            return;
        }

        try {
            List<String> lines = Files.readAllLines(path);
            List<String> newLines = new ArrayList<>();
            Set<String> updatedKeys = new HashSet<>();
            Pattern pattern = Pattern.compile("^([A-Z_]+)=(.*)$");

            for (String line : lines) {
                String trimmed = line.trim();
                
                // 빈 줄이나 주석은 그대로 유지
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    newLines.add(line);
                    continue;
                }

                Matcher matcher = pattern.matcher(trimmed);
                if (matcher.matches()) {
                    String key = matcher.group(1);
                    if (updates.containsKey(key)) {
                        // 업데이트할 키면 새 값으로 교체
                        newLines.add(key + "=" + updates.get(key));
                        updatedKeys.add(key);
                        log.info("Updated env variable: {} = {}", key, 
                                key.contains("KEY") || key.contains("PASSWORD") ? "****" : updates.get(key));
                    } else {
                        newLines.add(line);
                    }
                } else {
                    newLines.add(line);
                }
            }

            // 파일에 없던 새 키는 적절한 섹션에 추가
            for (Map.Entry<String, String> entry : updates.entrySet()) {
                if (!updatedKeys.contains(entry.getKey())) {
                    // 파일 끝에 추가
                    newLines.add(entry.getKey() + "=" + entry.getValue());
                    log.info("Added new env variable: {}", entry.getKey());
                }
            }

            Files.write(path, newLines);
            log.info("Environment file updated successfully");

        } catch (IOException e) {
            log.error("Failed to update environment file: {}", e.getMessage());
            throw new RuntimeException("Failed to update environment file", e);
        }
    }

    /**
     * DB 설정 키를 환경변수 키로 변환
     */
    public String configKeyToEnvKey(String configKey) {
        // llm.qwen3.api_url -> QWEN3_API_URL
        // llm.active_provider -> ACTIVE_LLM_PROVIDER
        // cli.aider.enabled -> AIDER_ENABLED

        if (configKey.equals("llm.active_provider")) {
            return "ACTIVE_LLM_PROVIDER";
        }

        return configKey
                .replace("llm.", "")
                .replace("cli.", "")
                .replace(".", "_")
                .replace("-", "_")
                .toUpperCase();
    }

    /**
     * 환경변수 키를 DB 설정 키로 변환
     */
    public String envKeyToConfigKey(String envKey) {
        // ACTIVE_LLM_PROVIDER -> llm.active_provider
        // QWEN3_API_URL -> llm.qwen3.api_url
        // AIDER_ENABLED -> cli.aider.enabled

        if (envKey.equals("ACTIVE_LLM_PROVIDER")) {
            return "llm.active_provider";
        }

        String lower = envKey.toLowerCase();

        if (lower.startsWith("qwen3_")) {
            return "llm.qwen3." + lower.substring(6).replace("_", "-");
        }
        if (lower.startsWith("gpt_oss_")) {
            return "llm.gpt-oss." + lower.substring(8).replace("_", "-");
        }
        if (lower.startsWith("aider_")) {
            return "cli.aider." + lower.substring(6).replace("_", "-");
        }
        if (lower.startsWith("fabric_")) {
            return "cli.fabric." + lower.substring(7).replace("_", "-");
        }

        return lower.replace("_", ".");
    }

    /**
     * 환경변수 파일 경로 확인
     */
    private Path resolveEnvFilePath() {
        Path path = Paths.get(envFilePath);
        if (!path.isAbsolute()) {
            // 상대 경로면 프로젝트 루트 기준
            path = Paths.get(System.getProperty("user.dir")).resolve(envFilePath);
        }
        return path;
    }

    /**
     * 환경변수 파일 존재 여부 확인
     */
    public boolean envFileExists() {
        return Files.exists(resolveEnvFilePath());
    }

    /**
     * 환경변수 파일 경로 반환
     */
    public String getEnvFilePath() {
        return resolveEnvFilePath().toString();
    }
}

package com.c2java.service;

import com.c2java.config.CliProperties;
import com.c2java.config.LlmProperties;
import com.c2java.domain.ConversionJob.JobStatus;
import com.c2java.dto.LlmConfigDto;
import com.c2java.dto.SystemStatusDto;
import com.c2java.repository.ConversionJobRepository;
import com.c2java.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.RuntimeMXBean;
import java.util.HashMap;
import java.util.Map;

/**
 * 관리자 서비스
 * 시스템 설정 및 상태 관리를 담당합니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminService {

    private final LlmProperties llmProperties;
    private final CliProperties cliProperties;
    private final ConversionJobRepository jobRepository;
    private final UserRepository userRepository;
    private final EnvSyncService envSyncService;
    private final FileStorageService fileStorageService;

    /**
     * 시스템 상태 조회
     */
    public SystemStatusDto getSystemStatus() {
        RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        
        return SystemStatusDto.builder()
                .status("RUNNING")
                .uptime(runtimeBean.getUptime())
                .heapMemoryUsed(memoryBean.getHeapMemoryUsage().getUsed())
                .heapMemoryMax(memoryBean.getHeapMemoryUsage().getMax())
                .activeLlmProvider(llmProperties.getActiveProvider())
                .aiderEnabled(cliProperties.getAider().isEnabled())
                .fabricEnabled(cliProperties.getFabric().isEnabled())
                .pendingJobs(jobRepository.countByStatus(JobStatus.PENDING))
                .runningJobs(jobRepository.countByStatus(JobStatus.CONVERTING))
                .completedJobs(jobRepository.countByStatus(JobStatus.COMPLETED))
                .failedJobs(jobRepository.countByStatus(JobStatus.FAILED))
                .build();
    }

    /**
     * LLM 설정 조회
     */
    public LlmConfigDto getLlmConfig() {
        return LlmConfigDto.builder()
                .activeProvider(llmProperties.getActiveProvider())
                .qwen3(mapLlmConfig(llmProperties.getQwen3(), "qwen3"))
                .gptOss(mapLlmConfig(llmProperties.getGptOss(), "gpt_oss"))
                .build();
    }

    /**
     * LLM 제공자 변경
     */
    public LlmConfigDto changeLlmProvider(String provider) {
        if (!provider.equals("qwen3") && !provider.equals("gpt_oss")) {
            throw new IllegalArgumentException("Invalid LLM provider: " + provider);
        }
        
        llmProperties.setActiveProvider(provider);
        log.info("LLM provider changed to: {}", provider);
        
        return getLlmConfig();
    }

    /**
     * LLM 설정 업데이트
     */
    public LlmConfigDto updateLlmConfig(LlmConfigDto config) {
        if (config.getActiveProvider() != null) {
            llmProperties.setActiveProvider(config.getActiveProvider());
        }
        
        if (config.getQwen3() != null) {
            updateProviderConfig(llmProperties.getQwen3(), config.getQwen3());
        }
        
        if (config.getGptOss() != null) {
            updateProviderConfig(llmProperties.getGptOss(), config.getGptOss());
        }
        
        log.info("LLM configuration updated");
        return getLlmConfig();
    }

    /**
     * 환경변수 조회 (민감정보 마스킹)
     */
    public Map<String, String> getEnvironmentVariables() {
        Map<String, String> env = new HashMap<>();
        
        env.put("ACTIVE_LLM_PROVIDER", llmProperties.getActiveProvider());
        env.put("QWEN3_API_URL", llmProperties.getQwen3().getApiUrl());
        env.put("QWEN3_API_KEY", maskSensitive(llmProperties.getQwen3().getApiKey()));
        env.put("QWEN3_MODEL_NAME", llmProperties.getQwen3().getModelName());
        env.put("GPT_OSS_API_URL", llmProperties.getGptOss().getApiUrl());
        env.put("GPT_OSS_API_KEY", maskSensitive(llmProperties.getGptOss().getApiKey()));
        env.put("GPT_OSS_MODEL_NAME", llmProperties.getGptOss().getModelName());
        env.put("AIDER_ENABLED", String.valueOf(cliProperties.getAider().isEnabled()));
        env.put("FABRIC_ENABLED", String.valueOf(cliProperties.getFabric().isEnabled()));
        
        return env;
    }

    /**
     * CLI 도구 상태 확인
     */
    public Map<String, Object> getCliStatus() {
        Map<String, Object> status = new HashMap<>();
        
        Map<String, Object> aiderStatus = new HashMap<>();
        aiderStatus.put("enabled", cliProperties.getAider().isEnabled());
        aiderStatus.put("executablePath", cliProperties.getAider().getExecutablePath());
        aiderStatus.put("autoCommits", cliProperties.getAider().isAutoCommits());
        status.put("aider", aiderStatus);
        
        Map<String, Object> fabricStatus = new HashMap<>();
        fabricStatus.put("enabled", cliProperties.getFabric().isEnabled());
        fabricStatus.put("executablePath", cliProperties.getFabric().getExecutablePath());
        fabricStatus.put("defaultPattern", cliProperties.getFabric().getDefaultPattern());
        status.put("fabric", fabricStatus);
        
        return status;
    }

    /**
     * 통계 정보 조회
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        stats.put("totalJobs", jobRepository.count());
        stats.put("pendingJobs", jobRepository.countByStatus(JobStatus.PENDING));
        stats.put("analyzingJobs", jobRepository.countByStatus(JobStatus.ANALYZING));
        stats.put("convertingJobs", jobRepository.countByStatus(JobStatus.CONVERTING));
        stats.put("compilingJobs", jobRepository.countByStatus(JobStatus.COMPILING));
        stats.put("testingJobs", jobRepository.countByStatus(JobStatus.TESTING));
        stats.put("reviewingJobs", jobRepository.countByStatus(JobStatus.REVIEWING));
        stats.put("completedJobs", jobRepository.countByStatus(JobStatus.COMPLETED));
        stats.put("failedJobs", jobRepository.countByStatus(JobStatus.FAILED));
        
        // 성공률 계산
        long total = jobRepository.count();
        long completed = jobRepository.countByStatus(JobStatus.COMPLETED);
        double successRate = total > 0 ? (double) completed / total * 100 : 0;
        stats.put("successRate", String.format("%.2f%%", successRate));
        
        return stats;
    }

    /**
     * 사용자 통계 조회
     */
    public Map<String, Object> getUserStats() {
        Map<String, Object> stats = new HashMap<>();
        
        stats.put("totalUsers", userRepository.count());
        stats.put("activeUsers", userRepository.countByIsActiveTrue());
        
        // 최근 로그인 사용자 목록
        var recentUsers = userRepository.findTop10ByOrderByLastLoginAtDesc().stream()
                .map(user -> {
                    Map<String, Object> userMap = new HashMap<>();
                    userMap.put("username", user.getUsername());
                    userMap.put("displayName", user.getDisplayName());
                    userMap.put("role", user.getRole().name());
                    userMap.put("lastLoginAt", user.getLastLoginAt());
                    return userMap;
                })
                .toList();
        stats.put("recentUsers", recentUsers);
        
        return stats;
    }

    /**
     * 환경변수 파일에서 LLM 설정 읽기
     */
    public Map<String, String> getLlmEnvVariables() {
        return envSyncService.loadLlmEnvVariables();
    }

    /**
     * 환경변수 파일에서 CLI 설정 읽기
     */
    public Map<String, String> getCliEnvVariables() {
        return envSyncService.loadCliEnvVariables();
    }

    /**
     * 환경변수 파일에서 워커 서버 설정 읽기
     */
    public Map<String, String> getWorkerServerEnvVariables() {
        return envSyncService.loadWorkerServerEnvVariables();
    }

    /**
     * LLM 설정을 환경변수 파일에 저장
     */
    public void saveLlmEnvVariables(Map<String, String> envVars) {
        envSyncService.saveEnvVariables(envVars);
        log.info("LLM environment variables saved");
    }

    /**
     * CLI 설정을 환경변수 파일에 저장
     */
    public void saveCliEnvVariables(Map<String, String> envVars) {
        envSyncService.saveEnvVariables(envVars);
        log.info("CLI environment variables saved");
    }

    /**
     * 워커 서버 설정을 환경변수 파일에 저장
     */
    public void saveWorkerServerEnvVariables(Map<String, String> envVars) {
        envSyncService.saveEnvVariables(envVars);
        log.info("Worker server environment variables saved");
    }

    /**
     * 환경변수 파일 경로 및 존재 여부
     */
    public Map<String, Object> getEnvFileInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("path", envSyncService.getEnvFilePath());
        info.put("exists", envSyncService.envFileExists());
        return info;
    }

    /**
     * 파일 서버 상태 조회
     */
    public Map<String, Object> getFileServerStatus() {
        return fileStorageService.getServerStatus();
    }

    /**
     * 파일 서버 연결 테스트
     */
    public Map<String, Object> testFileServerConnection() {
        boolean connected = fileStorageService.testConnection();
        Map<String, Object> result = new HashMap<>();
        result.put("connected", connected);
        result.put("message", connected ? "파일 서버 연결 성공" : "파일 서버 연결 실패");
        return result;
    }

    /**
     * CLI 도구 연결 테스트
     */
    public Map<String, Object> testCliConnection(String tool, String token, String apiKey) {
        Map<String, Object> result = new HashMap<>();
        boolean success = false;
        String message = "";
        String details = "";

        try {
            if ("cursor".equalsIgnoreCase(tool)) {
                // Cursor Agent CLI 실행 테스트
                log.info("Testing Cursor Agent CLI connection...");
                
                // CLI 경로 찾기 (여러 가능한 위치 시도)
                String[] possiblePaths = {
                    "/Users/dongsoo/.local/bin/agent",
                    System.getProperty("user.home") + "/.local/bin/agent",
                    "/usr/local/bin/agent",
                    "agent" // PATH에서 찾기
                };
                
                String cliPath = null;
                for (String path : possiblePaths) {
                    java.io.File file = new java.io.File(path);
                    if (file.exists() && file.canExecute()) {
                        cliPath = path;
                        break;
                    }
                }
                
                if (cliPath == null) {
                    cliPath = "agent"; // PATH에서 찾기 시도
                }
                
                try {
                    ProcessBuilder pb = new ProcessBuilder(cliPath, "--version");
                    pb.redirectErrorStream(true);
                    Process process = pb.start();
                    
                    java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(process.getInputStream())
                    );
                    StringBuilder output = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append("\n");
                    }
                    
                    int exitCode = process.waitFor();
                    details = output.toString().trim();
                    
                    if (exitCode == 0) {
                        success = true;
                        message = "✓ Cursor Agent CLI가 정상적으로 설치되어 있습니다.";
                        log.info("Cursor Agent CLI test successful: {}", details);
                    } else {
                        message = "✗ Cursor Agent CLI 실행 실패 (Exit Code: " + exitCode + ")";
                        log.warn("Cursor Agent CLI test failed: {}", details);
                    }
                } catch (java.io.IOException e) {
                    message = "✗ Cursor Agent CLI를 찾을 수 없습니다. 설치 경로를 확인해주세요.";
                    details = "경로: /Users/dongsoo/.local/bin/agent";
                    log.error("Cursor Agent CLI not found", e);
                } catch (InterruptedException e) {
                    message = "✗ Cursor Agent CLI 테스트 중 인터럽트 발생";
                    log.error("Cursor Agent CLI test interrupted", e);
                    Thread.currentThread().interrupt();
                }
                
            } else if ("claude".equalsIgnoreCase(tool)) {
                // Claude API Key 실제 테스트 (간단한 API 호출)
                log.info("Testing Claude API connection...");
                
                if (apiKey == null || apiKey.isEmpty()) {
                    message = "✗ Claude API Key가 입력되지 않았습니다.";
                } else if (!apiKey.startsWith("sk-ant-")) {
                    message = "✗ 유효하지 않은 Claude API Key 형식입니다. (sk-ant- 로 시작해야 함)";
                } else {
                    try {
                        // WebClient를 사용한 실제 API 테스트
                        org.springframework.web.reactive.function.client.WebClient client = 
                            org.springframework.web.reactive.function.client.WebClient.builder()
                                .baseUrl("https://api.anthropic.com")
                                .defaultHeader("x-api-key", apiKey)
                                .defaultHeader("anthropic-version", "2023-06-01")
                                .defaultHeader("Content-Type", "application/json")
                                .build();
                        
                        // 간단한 메시지 테스트 (최소 토큰 사용)
                        Map<String, Object> requestBody = Map.of(
                            "model", "claude-3-5-sonnet-20240620",
                            "max_tokens", 10,
                            "messages", java.util.List.of(
                                Map.of("role", "user", "content", "Hi")
                            )
                        );
                        
                        Map<String, Object> response = client.post()
                            .uri("/v1/messages")
                            .bodyValue(requestBody)
                            .retrieve()
                            .bodyToMono(Map.class)
                            .block(java.time.Duration.ofSeconds(10));
                        
                        if (response != null && response.containsKey("id")) {
                            success = true;
                            message = "✓ Claude API 연결이 정상적으로 작동합니다.";
                            details = "Model: " + response.get("model");
                            log.info("Claude API test successful");
                        } else {
                            message = "✗ Claude API 응답이 올바르지 않습니다.";
                            log.warn("Claude API unexpected response: {}", response);
                        }
                        
                    } catch (org.springframework.web.reactive.function.client.WebClientResponseException e) {
                        message = "✗ Claude API 인증 실패: " + e.getStatusCode();
                        details = e.getResponseBodyAsString();
                        log.error("Claude API authentication failed", e);
                    } catch (Exception e) {
                        message = "✗ Claude API 연결 실패: " + e.getMessage();
                        log.error("Claude API connection failed", e);
                    }
                }
                
            } else {
                message = "✗ 지원하지 않는 CLI 도구입니다: " + tool;
            }
        } catch (Exception e) {
            log.error("CLI connection test failed", e);
            message = "✗ 연결 테스트 중 오류가 발생했습니다: " + e.getMessage();
        }

        result.put("success", success);
        result.put("message", message);
        if (!details.isEmpty()) {
            result.put("details", details);
        }
        return result;
    }

    /**
     * LLM 설정 매핑
     */
    private LlmConfigDto.ProviderConfig mapLlmConfig(LlmProperties.LlmConfig config, String name) {
        return LlmConfigDto.ProviderConfig.builder()
                .name(name)
                .apiUrl(config.getApiUrl())
                .apiKey(maskSensitive(config.getApiKey()))
                .modelName(config.getModelName())
                .maxTokens(config.getMaxTokens())
                .temperature(config.getTemperature())
                .build();
    }

    /**
     * 제공자 설정 업데이트
     */
    private void updateProviderConfig(LlmProperties.LlmConfig target, LlmConfigDto.ProviderConfig source) {
        if (source.getApiUrl() != null) {
            target.setApiUrl(source.getApiUrl());
        }
        if (source.getApiKey() != null && !source.getApiKey().contains("*")) {
            target.setApiKey(source.getApiKey());
        }
        if (source.getModelName() != null) {
            target.setModelName(source.getModelName());
        }
        if (source.getMaxTokens() > 0) {
            target.setMaxTokens(source.getMaxTokens());
        }
        if (source.getTemperature() >= 0) {
            target.setTemperature(source.getTemperature());
        }
    }

    /**
     * 워크스페이스 디렉토리 열기
     */
    public boolean openWorkspaceDirectory(String path) {
        try {
            if (path == null || path.isEmpty()) {
                return false;
            }
            
            File directory = new File(path);
            if (!directory.exists() || !directory.isDirectory()) {
                return false;
            }

            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                Runtime.getRuntime().exec(new String[]{"explorer", path});
            } else if (os.contains("mac")) {
                Runtime.getRuntime().exec(new String[]{"open", path});
            } else if (os.contains("nix") || os.contains("nux") || os.contains("aix")) {
                Runtime.getRuntime().exec(new String[]{"xdg-open", path});
            } else {
                return false;
            }
            return true;
        } catch (IOException e) {
            log.error("Failed to open workspace directory: {}", path, e);
            return false;
        }
    }

    /**
     * 워커 서버 상태 확인
     */
    public Map<String, Object> getWorkerServerStatus() {
        Map<String, Object> status = new HashMap<>();
        Map<String, String> workerEnv = getWorkerServerEnvVariables();
        
        String workerServerUrl = workerEnv.get("WORKER_SERVER_URL");
        boolean isWorkerEnabled = workerServerUrl != null && !workerServerUrl.isEmpty() 
                                && !workerServerUrl.contains("localhost") 
                                && !workerServerUrl.startsWith("http://192.168.");
        
        status.put("enabled", isWorkerEnabled);
        status.put("url", workerServerUrl);
        
        if (isWorkerEnabled) {
            try {
                // 워커 서버의 CLI 서비스 health 체크
                String healthUrl = workerServerUrl.replaceAll("/api$", "") + "/health";
                org.springframework.web.reactive.function.client.WebClient webClient = 
                    org.springframework.web.reactive.function.client.WebClient.builder()
                        .baseUrl(healthUrl)
                        .build();
                        
                Map<String, Object> healthResponse = webClient.get()
                    .retrieve()
                    .bodyToMono(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                    .block(java.time.Duration.ofSeconds(5));
                    
                if (healthResponse != null && "healthy".equals(healthResponse.get("status"))) {
                    status.put("cliService", Map.of(
                        "running", true,
                        "message", "CLI Service가 정상 작동 중입니다.",
                        "details", healthResponse
                    ));
                } else {
                    status.put("cliService", Map.of(
                        "running", false,
                        "message", "CLI Service 상태를 확인할 수 없습니다."
                    ));
                }
            } catch (Exception e) {
                log.warn("워커 서버 상태 확인 실패: {}", e.getMessage());
                status.put("cliService", Map.of(
                    "running", false,
                    "message", "CLI Service에 연결할 수 없습니다: " + e.getMessage(),
                    "error", e.getClass().getSimpleName()
                ));
            }
        } else {
            status.put("cliService", Map.of(
                "running", false,
                "message", "워커 서버가 설정되지 않았습니다."
            ));
        }
        
        return status;
    }

    /**
     * 민감 정보 마스킹
     */
    private String maskSensitive(String value) {
        if (value == null || value.length() < 8) {
            return "****";
        }
        return value.substring(0, 4) + "****" + value.substring(value.length() - 4);
    }
}

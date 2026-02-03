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
     * 민감 정보 마스킹
     */
    private String maskSensitive(String value) {
        if (value == null || value.length() < 8) {
            return "****";
        }
        return value.substring(0, 4) + "****" + value.substring(value.length() - 4);
    }
}

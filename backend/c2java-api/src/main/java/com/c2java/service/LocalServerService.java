package com.c2java.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 로컬 서버 관리 서비스
 * Airflow, CLI Service 등의 로컬 서비스를 관리합니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LocalServerService {

    // 서비스 프로세스 저장
    private final Map<String, Process> runningProcesses = new ConcurrentHashMap<>();
    
    // 서비스 로그 저장 (최근 1000줄)
    private final Map<String, LinkedList<String>> serviceLogs = new ConcurrentHashMap<>();
    private static final int MAX_LOG_LINES = 1000;
    
    private final EnvSyncService envSyncService;
    private final DockerApiService dockerApiService;

    /**
     * 서비스 상태 조회
     */
    public Map<String, Object> getServiceStatus(String serviceName) {
        Map<String, Object> status = new HashMap<>();
        
        switch (serviceName.toLowerCase()) {
            case "airflow":
                return getAirflowStatus();
            case "cli-service":
                return getCliServiceStatus();
            default:
                status.put("running", false);
                status.put("error", "Unknown service: " + serviceName);
                return status;
        }
    }

    /**
     * 모든 서비스 상태 조회
     */
    public Map<String, Map<String, Object>> getAllServicesStatus() {
        Map<String, Map<String, Object>> allStatus = new HashMap<>();
        allStatus.put("airflow", getAirflowStatus());
        allStatus.put("cli-service", getCliServiceStatus());
        return allStatus;
    }

    /**
     * Airflow 상태 확인
     */
    private Map<String, Object> getAirflowStatus() {
        try {
            // Webserver와 Scheduler 상태 확인
            Map<String, Object> webserverStatus = dockerApiService.getContainerStatus("c2java-airflow-webserver");
            Map<String, Object> schedulerStatus = dockerApiService.getContainerStatus("c2java-airflow-scheduler");
            
            boolean webserverRunning = Boolean.TRUE.equals(webserverStatus.get("running"));
            boolean schedulerRunning = Boolean.TRUE.equals(schedulerStatus.get("running"));
            boolean bothRunning = webserverRunning && schedulerRunning;
            
            Map<String, Object> status = new HashMap<>();
            status.put("running", bothRunning);
            status.put("type", "docker");
            status.put("url", "http://localhost:8081");
            status.put("webserver", webserverStatus);
            status.put("scheduler", schedulerStatus);
            
            if (bothRunning) {
                status.put("message", "Airflow가 정상 실행 중입니다.");
            } else if (webserverRunning || schedulerRunning) {
                status.put("message", "Airflow가 부분적으로 실행 중입니다.");
            } else {
                status.put("message", "Airflow가 실행되고 있지 않습니다.");
            }
            
            return status;
            
        } catch (Exception e) {
            log.error("Failed to check Airflow status", e);
            Map<String, Object> status = new HashMap<>();
            status.put("running", false);
            status.put("error", e.getMessage());
            return status;
        }
    }

    /**
     * CLI Service 상태 확인
     */
    private Map<String, Object> getCliServiceStatus() {
        try {
            Map<String, Object> status = dockerApiService.getContainerStatus("c2java-cli");
            status.put("type", "docker");
            status.put("url", "http://localhost:8083");
            return status;
        } catch (Exception e) {
            log.error("Failed to check CLI Service status", e);
            Map<String, Object> status = new HashMap<>();
            status.put("running", false);
            status.put("error", e.getMessage());
            return status;
        }
    }

    /**
     * Airflow 시작
     */
    public Map<String, Object> startAirflow() {
        try {
            log.info("Starting Airflow containers via Docker API...");
            
            // Webserver와 Scheduler 시작
            Map<String, Object> webserverResult = dockerApiService.startContainer("c2java-airflow-webserver");
            Map<String, Object> schedulerResult = dockerApiService.startContainer("c2java-airflow-scheduler");
            
            boolean webserverSuccess = Boolean.TRUE.equals(webserverResult.get("success"));
            boolean schedulerSuccess = Boolean.TRUE.equals(schedulerResult.get("success"));
            
            Map<String, Object> result = new HashMap<>();
            if (webserverSuccess && schedulerSuccess) {
                result.put("success", true);
                result.put("message", "Airflow가 시작되었습니다. 잠시 후 http://localhost:8081 에서 확인하세요.");
            } else {
                result.put("success", false);
                result.put("message", "Airflow 시작 중 일부 실패: " +
                        "\nWebserver: " + webserverResult.get("message") +
                        "\nScheduler: " + schedulerResult.get("message"));
            }
            result.put("webserver", webserverResult);
            result.put("scheduler", schedulerResult);
            
            return result;
            
        } catch (Exception e) {
            log.error("Failed to start Airflow", e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "Airflow 시작 중 오류: " + e.getMessage());
            return result;
        }
    }

    /**
     * Airflow 중지
     */
    public Map<String, Object> stopAirflow() {
        try {
            log.info("Stopping Airflow containers via Docker API...");
            
            // Webserver와 Scheduler 중지
            Map<String, Object> webserverResult = dockerApiService.stopContainer("c2java-airflow-webserver");
            Map<String, Object> schedulerResult = dockerApiService.stopContainer("c2java-airflow-scheduler");
            
            boolean webserverSuccess = Boolean.TRUE.equals(webserverResult.get("success"));
            boolean schedulerSuccess = Boolean.TRUE.equals(schedulerResult.get("success"));
            
            Map<String, Object> result = new HashMap<>();
            if (webserverSuccess && schedulerSuccess) {
                result.put("success", true);
                result.put("message", "Airflow가 중지되었습니다.");
            } else {
                result.put("success", false);
                result.put("message", "Airflow 중지 중 일부 실패: " +
                        "\nWebserver: " + webserverResult.get("message") +
                        "\nScheduler: " + schedulerResult.get("message"));
            }
            result.put("webserver", webserverResult);
            result.put("scheduler", schedulerResult);
            
            return result;
            
        } catch (Exception e) {
            log.error("Failed to stop Airflow", e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "Airflow 중지 중 오류: " + e.getMessage());
            return result;
        }
    }

    /**
     * CLI Service 시작
     */
    public Map<String, Object> startCliService() {
        try {
            log.info("Starting CLI Service container via Docker API...");
            return dockerApiService.startContainer("c2java-cli");
        } catch (Exception e) {
            log.error("Failed to start CLI Service", e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "CLI Service 시작 중 오류: " + e.getMessage());
            return result;
        }
    }

    /**
     * CLI Service 중지
     */
    public Map<String, Object> stopCliService() {
        try {
            log.info("Stopping CLI Service container via Docker API...");
            return dockerApiService.stopContainer("c2java-cli");
        } catch (Exception e) {
            log.error("Failed to stop CLI Service", e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "CLI Service 중지 중 오류: " + e.getMessage());
            return result;
        }
    }

    /**
     * 서비스 로그 조회
     */
    public List<String> getServiceLogs(String serviceName, Integer lines) {
        try {
            // Docker 컨테이너 로그 가져오기
            String containerName = getContainerNameForService(serviceName);
            if (containerName != null) {
                log.debug("Fetching logs for service: {} (container: {})", serviceName, containerName);
                return dockerApiService.getContainerLogs(containerName, lines != null ? lines : 100);
            }
            
            // Docker 컨테이너가 아닌 경우 메모리에서 로그 반환
            LinkedList<String> logs = serviceLogs.getOrDefault(serviceName, new LinkedList<>());
            
            if (lines == null || lines <= 0) {
                return new ArrayList<>(logs);
            }
            
            // 최근 N줄만 반환
            int fromIndex = Math.max(0, logs.size() - lines);
            return new ArrayList<>(logs.subList(fromIndex, logs.size()));
            
        } catch (Exception e) {
            log.error("Failed to get service logs for: {}", serviceName, e);
            return new ArrayList<>();
        }
    }
    
    /**
     * 서비스 이름으로 컨테이너 이름 가져오기
     */
    private String getContainerNameForService(String serviceName) {
        switch (serviceName.toLowerCase()) {
            case "airflow":
                // Airflow는 webserver와 scheduler 두 개의 컨테이너가 있음
                // 여기서는 webserver 로그를 기본으로 반환
                return "c2java-airflow-webserver";
            case "cli-service":
            case "cli":
                return "c2java-cli";
            default:
                return null;
        }
    }

    /**
     * 로그 추가 (내부 메서드)
     */
    private void appendLog(String serviceName, String logLine) {
        LinkedList<String> logs = serviceLogs.computeIfAbsent(serviceName, k -> new LinkedList<>());
        
        String timestampedLog = String.format("[%s] %s", 
            java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
            logLine
        );
        
        logs.add(timestampedLog);
        
        // 최대 로그 라인 수 제한
        while (logs.size() > MAX_LOG_LINES) {
            logs.removeFirst();
        }
    }

    /**
     * 로그 초기화
     */
    public void clearLogs(String serviceName) {
        serviceLogs.remove(serviceName);
        log.info("Cleared logs for service: {}", serviceName);
    }
}

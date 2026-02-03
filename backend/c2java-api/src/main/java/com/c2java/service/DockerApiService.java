package com.c2java.service;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Docker Engine API를 직접 호출하여 컨테이너를 제어하는 서비스
 * Docker CLI 없이 Docker 소켓을 통해 직접 제어
 */
@Service
@Slf4j
public class DockerApiService {

    private final DockerClient dockerClient;

    public DockerApiService() {
        try {
            // Docker 클라이언트 설정
            DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                    .withDockerHost("unix:///var/run/docker.sock")
                    .build();

            // HTTP 클라이언트 생성
            DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                    .dockerHost(config.getDockerHost())
                    .maxConnections(100)
                    .connectionTimeout(Duration.ofSeconds(30))
                    .responseTimeout(Duration.ofSeconds(45))
                    .build();

            this.dockerClient = DockerClientImpl.getInstance(config, httpClient);
            log.info("Docker API client initialized successfully");
            
        } catch (Exception e) {
            log.error("Failed to initialize Docker API client", e);
            throw new RuntimeException("Docker API client initialization failed", e);
        }
    }

    @PreDestroy
    public void cleanup() {
        try {
            if (dockerClient != null) {
                dockerClient.close();
            }
        } catch (Exception e) {
            log.error("Error closing Docker client", e);
        }
    }

    /**
     * 컨테이너 목록 조회
     */
    public List<Container> listContainers(String nameFilter) {
        try {
            List<Container> containers;
            if (nameFilter != null) {
                containers = dockerClient.listContainersCmd()
                        .withShowAll(true)
                        .withNameFilter(List.of(nameFilter))
                        .exec();
            } else {
                containers = dockerClient.listContainersCmd()
                        .withShowAll(true)
                        .exec();
            }
            log.debug("Listed {} containers with filter '{}'", containers.size(), nameFilter);
            return containers;
        } catch (Exception e) {
            log.error("Failed to list containers", e);
            return List.of();
        }
    }

    /**
     * 컨테이너 시작
     */
    public Map<String, Object> startContainer(String containerName) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            log.info("Starting container: {}", containerName);
            
            // 컨테이너 ID 조회
            String containerId = findContainerIdByName(containerName);
            if (containerId == null) {
                result.put("success", false);
                result.put("message", "컨테이너를 찾을 수 없습니다: " + containerName);
                return result;
            }

            // 컨테이너 시작
            dockerClient.startContainerCmd(containerId).exec();

            result.put("success", true);
            result.put("message", containerName + " 컨테이너가 시작되었습니다.");
            log.info("Container started successfully: {}", containerName);
            
        } catch (Exception e) {
            log.error("Failed to start container: {}", containerName, e);
            result.put("success", false);
            result.put("message", "컨테이너 시작 실패: " + e.getMessage());
        }
        
        return result;
    }

    /**
     * 컨테이너 중지
     */
    public Map<String, Object> stopContainer(String containerName) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            log.info("Stopping container: {}", containerName);
            
            // 컨테이너 ID 조회
            String containerId = findContainerIdByName(containerName);
            if (containerId == null) {
                result.put("success", false);
                result.put("message", "컨테이너를 찾을 수 없습니다: " + containerName);
                return result;
            }

            // 컨테이너 중지 (10초 타임아웃)
            dockerClient.stopContainerCmd(containerId)
                    .withTimeout(10)
                    .exec();

            result.put("success", true);
            result.put("message", containerName + " 컨테이너가 중지되었습니다.");
            log.info("Container stopped successfully: {}", containerName);
            
        } catch (Exception e) {
            log.error("Failed to stop container: {}", containerName, e);
            result.put("success", false);
            result.put("message", "컨테이너 중지 실패: " + e.getMessage());
        }
        
        return result;
    }

    /**
     * 컨테이너 상태 확인
     */
    public Map<String, Object> getContainerStatus(String containerName) {
        Map<String, Object> status = new HashMap<>();
        
        try {
            String containerId = findContainerIdByName(containerName);
            if (containerId == null) {
                status.put("running", false);
                status.put("message", "컨테이너를 찾을 수 없습니다.");
                return status;
            }

            InspectContainerResponse containerInfo = dockerClient.inspectContainerCmd(containerId).exec();
            InspectContainerResponse.ContainerState state = containerInfo.getState();
            
            Boolean isRunning = state != null && Boolean.TRUE.equals(state.getRunning());
            
            status.put("running", isRunning);
            status.put("containerId", containerId);
            status.put("status", state != null ? state.getStatus() : "unknown");
            status.put("message", isRunning ? "실행 중" : "중지됨");
            
        } catch (Exception e) {
            log.error("Failed to get container status: {}", containerName, e);
            status.put("running", false);
            status.put("error", e.getMessage());
        }
        
        return status;
    }

    /**
     * 컨테이너 로그 조회
     */
    public List<String> getContainerLogs(String containerName, Integer tailLines) {
        List<String> logs = new ArrayList<>();
        
        try {
            String containerId = findContainerIdByName(containerName);
            if (containerId == null) {
                log.warn("Container not found for logs: {}", containerName);
                return logs;
            }

            int lines = (tailLines != null && tailLines > 0) ? tailLines : 100;
            
            // 컨테이너 로그 조회
            try (var logStream = dockerClient.logContainerCmd(containerId)
                    .withStdOut(true)
                    .withStdErr(true)
                    .withTail(lines)
                    .exec(new com.github.dockerjava.core.command.LogContainerResultCallback())
                    .awaitCompletion()) {
                
                // LogContainerResultCallback에서 로그 수집
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Interrupted while fetching container logs", e);
            }
            
            // LogContainerResultCallback 직접 사용
            var callback = new com.github.dockerjava.core.command.LogContainerResultCallback() {
                @Override
                public void onNext(com.github.dockerjava.api.model.Frame frame) {
                    if (frame != null && frame.getPayload() != null) {
                        String logLine = new String(frame.getPayload()).trim();
                        if (!logLine.isEmpty()) {
                            logs.add(logLine);
                        }
                    }
                    super.onNext(frame);
                }
            };
            
            dockerClient.logContainerCmd(containerId)
                    .withStdOut(true)
                    .withStdErr(true)
                    .withTail(lines)
                    .exec(callback)
                    .awaitCompletion();
            
            log.debug("Fetched {} log lines from container: {}", logs.size(), containerName);
            
        } catch (Exception e) {
            log.error("Failed to get container logs: {}", containerName, e);
        }
        
        return logs;
    }

    /**
     * 컨테이너 이름으로 ID 찾기
     */
    private String findContainerIdByName(String containerName) {
        try {
            // 모든 컨테이너 조회 후 이름으로 필터링
            List<Container> containers = dockerClient.listContainersCmd()
                    .withShowAll(true)
                    .exec();

            for (Container container : containers) {
                // Docker는 컨테이너 이름 앞에 /를 붙여 반환 (예: /c2java-cli)
                for (String name : container.getNames()) {
                    String cleanName = name.startsWith("/") ? name.substring(1) : name;
                    if (cleanName.equals(containerName)) {
                        log.debug("Found container '{}' with ID: {}", containerName, container.getId());
                        return container.getId();
                    }
                }
            }
            
            log.warn("Container not found: {}", containerName);
            
        } catch (Exception e) {
            log.error("Failed to find container ID for: {}", containerName, e);
        }
        
        return null;
    }
}

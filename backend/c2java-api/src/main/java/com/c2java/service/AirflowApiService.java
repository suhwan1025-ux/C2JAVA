package com.c2java.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Airflow REST API 연동 서비스
 * DAG 실행 및 상태 모니터링
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AirflowApiService {

    @Value("${airflow.url:http://localhost:8081}")
    private String airflowUrl;

    @Value("${airflow.username:admin}")
    private String airflowUsername;

    @Value("${airflow.password:admin}")
    private String airflowPassword;

    /**
     * DAG 트리거 (실행)
     */
    public Map<String, Object> triggerDag(String dagId, Map<String, Object> config) {
        WebClient client = createWebClient();
        
        log.info("Triggering DAG: {}", dagId);
        
        Map<String, Object> request = new HashMap<>();
        request.put("conf", config != null ? config : Map.of());
        
        try {
            Map<String, Object> response = client.post()
                    .uri("/api/v1/dags/{dag_id}/dagRuns", dagId)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            
            log.info("DAG triggered successfully: {}", response.get("dag_run_id"));
            return response;
        } catch (Exception e) {
            log.error("Failed to trigger DAG: {}", dagId, e);
            throw new RuntimeException("Airflow DAG 실행 실패: " + e.getMessage(), e);
        }
    }

    /**
     * DAG 실행 상태 조회
     */
    public Map<String, Object> getDagRunStatus(String dagId, String dagRunId) {
        WebClient client = createWebClient();
        
        try {
            Map<String, Object> response = client.get()
                    .uri("/api/v1/dags/{dag_id}/dagRuns/{dag_run_id}", dagId, dagRunId)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            
            return response;
        } catch (Exception e) {
            log.error("Failed to get DAG run status: {} / {}", dagId, dagRunId, e);
            return Map.of("state", "unknown", "error", e.getMessage());
        }
    }

    /**
     * 특정 Task 상태 조회
     */
    public Map<String, Object> getTaskInstanceStatus(String dagId, String dagRunId, String taskId) {
        WebClient client = createWebClient();
        
        try {
            Map<String, Object> response = client.get()
                    .uri("/api/v1/dags/{dag_id}/dagRuns/{dag_run_id}/taskInstances/{task_id}",
                            dagId, dagRunId, taskId)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            
            return response;
        } catch (Exception e) {
            log.error("Failed to get task status: {}", taskId, e);
            return Map.of("state", "unknown");
        }
    }

    /**
     * 모든 Task 인스턴스 조회
     */
    public Map<String, Object> getAllTaskInstances(String dagId, String dagRunId) {
        WebClient client = createWebClient();
        
        try {
            Map<String, Object> response = client.get()
                    .uri("/api/v1/dags/{dag_id}/dagRuns/{dag_run_id}/taskInstances",
                            dagId, dagRunId)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            
            return response;
        } catch (Exception e) {
            log.error("Failed to get task instances", e);
            return Map.of("task_instances", new Object[0]);
        }
    }

    /**
     * DAG 일시정지/재개
     */
    public void pauseDag(String dagId, boolean isPaused) {
        WebClient client = createWebClient();
        
        try {
            client.patch()
                    .uri("/api/v1/dags/{dag_id}", dagId)
                    .bodyValue(Map.of("is_paused", isPaused))
                    .retrieve()
                    .bodyToMono(Void.class)
                    .block();
            
            log.info("DAG {} {}", dagId, isPaused ? "paused" : "resumed");
        } catch (Exception e) {
            log.error("Failed to update DAG pause state", e);
        }
    }

    /**
     * DAG 삭제
     */
    public void deleteDag(String dagId) {
        WebClient client = createWebClient();
        
        try {
            client.delete()
                    .uri("/api/v1/dags/{dag_id}", dagId)
                    .retrieve()
                    .bodyToMono(Void.class)
                    .block();
            
            log.info("DAG deleted: {}", dagId);
        } catch (Exception e) {
            log.error("Failed to delete DAG: {}", dagId, e);
        }
    }
    
    /**
     * DAG Run 삭제 (완전 제거)
     */
    public void deleteDagRun(String dagId, String dagRunId) {
        WebClient client = createWebClient();
        
        try {
            client.delete()
                    .uri("/api/v1/dags/{dag_id}/dagRuns/{dag_run_id}", dagId, dagRunId)
                    .retrieve()
                    .bodyToMono(Void.class)
                    .block();
            
            log.info("DAG Run deleted: {} / {}", dagId, dagRunId);
        } catch (Exception e) {
            log.error("Failed to delete DAG Run: {} / {}", dagId, dagRunId, e);
            throw new RuntimeException("DAG Run 삭제 실패: " + e.getMessage(), e);
        }
    }

    /**
     * DAG Run 취소 (실행 중단)
     */
    public Map<String, Object> cancelDagRun(String dagId, String dagRunId) {
        WebClient client = createWebClient();
        
        log.info("Cancelling DAG Run: {} / {}", dagId, dagRunId);
        
        try {
            // 1. 먼저 실행 중인 모든 Task Instance 취소
            Map<String, Object> taskInstances = getAllTaskInstances(dagId, dagRunId);
            if (taskInstances != null && taskInstances.containsKey("task_instances")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> tasks = (List<Map<String, Object>>) taskInstances.get("task_instances");
                
                for (Map<String, Object> task : tasks) {
                    String taskId = (String) task.get("task_id");
                    String state = (String) task.get("state");
                    
                    // running이나 queued 상태인 Task만 취소
                    if ("running".equals(state) || "queued".equals(state)) {
                        try {
                            log.info("Cancelling task instance: {}", taskId);
                            clearTaskInstance(dagId, dagRunId, taskId);
                        } catch (Exception e) {
                            log.warn("Failed to cancel task {}: {}", taskId, e.getMessage());
                        }
                    }
                }
            }
            
            // 2. DAG Run 상태를 failed로 변경
            Map<String, Object> request = Map.of("state", "failed");
            
            Map<String, Object> response = client.patch()
                    .uri("/api/v1/dags/{dag_id}/dagRuns/{dag_run_id}", dagId, dagRunId)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            
            log.info("DAG Run cancelled successfully: {}", dagRunId);
            return response != null ? response : Map.of("state", "cancelled");
            
        } catch (Exception e) {
            log.error("Failed to cancel DAG Run: {} / {}", dagId, dagRunId, e);
            throw new RuntimeException("Airflow DAG 실행 취소 실패: " + e.getMessage(), e);
        }
    }
    
    /**
     * Task Instance 취소 (Clear)
     */
    public void clearTaskInstance(String dagId, String dagRunId, String taskId) {
        WebClient client = createWebClient();
        
        try {
            // Task Instance의 상태를 failed로 변경
            Map<String, Object> request = Map.of(
                "dry_run", false,
                "task_ids", List.of(taskId),
                "dag_run_id", dagRunId,
                "include_upstream", false,
                "include_downstream", false,
                "include_future", false,
                "include_past", false,
                "reset_dag_runs", false,
                "only_failed", false,
                "only_running", true
            );
            
            client.post()
                    .uri("/api/v1/dags/{dag_id}/clearTaskInstances", dagId)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(Void.class)
                    .block();
            
            log.info("Task instance cleared: {} / {} / {}", dagId, dagRunId, taskId);
            
        } catch (Exception e) {
            log.error("Failed to clear task instance: {}", taskId, e);
            // Task 취소 실패는 무시하고 계속 진행
        }
    }

    /**
     * WebClient 생성 (Basic Auth)
     */
    private WebClient createWebClient() {
        String auth = airflowUsername + ":" + airflowPassword;
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
        
        return WebClient.builder()
                .baseUrl(airflowUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + encodedAuth)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}

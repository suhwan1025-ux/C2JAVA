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

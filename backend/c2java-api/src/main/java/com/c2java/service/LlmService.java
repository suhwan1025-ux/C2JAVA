package com.c2java.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * LLM API 연동 서비스
 * QWEN3 또는 GPT OSS와 통신하여 코드 변환
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LlmService {

    private final EnvSyncService envSyncService;

    @Value("${llm.timeout:180}")
    private int timeoutSeconds;

    /**
     * LLM API 호출하여 코드 변환
     */
    public String convertCode(String sourceCode, String conversionRules, String structureRules, String prompt) {
        try {
            // 환경 변수에서 LLM 설정 로드
            Map<String, String> llmConfig = envSyncService.loadLlmEnvVariables();
            String provider = llmConfig.getOrDefault("ACTIVE_LLM_PROVIDER", "qwen3");
            
            log.info("Using LLM provider: {}", provider);
            
            if ("qwen3".equals(provider)) {
                return callQwen3(llmConfig, sourceCode, conversionRules, structureRules, prompt);
            } else {
                return callGptOss(llmConfig, sourceCode, conversionRules, structureRules, prompt);
            }
        } catch (Exception e) {
            log.error("LLM API call failed", e);
            throw new RuntimeException("LLM API 호출 실패: " + e.getMessage(), e);
        }
    }

    /**
     * QWEN3 API 호출
     */
    private String callQwen3(Map<String, String> config, String sourceCode, 
                             String conversionRules, String structureRules, String prompt) {
        String apiUrl = config.get("QWEN3_API_URL");
        String apiKey = config.get("QWEN3_API_KEY");
        String model = config.get("QWEN3_MODEL_NAME");
        int maxTokens = Integer.parseInt(config.getOrDefault("QWEN3_MAX_TOKENS", "8192"));
        double temperature = Double.parseDouble(config.getOrDefault("QWEN3_TEMPERATURE", "0.1"));
        
        WebClient client = WebClient.builder()
                .baseUrl(apiUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .build();
        
        String fullPrompt = buildPrompt(sourceCode, conversionRules, structureRules, prompt);
        
        Map<String, Object> request = new HashMap<>();
        request.put("model", model);
        request.put("messages", new Object[]{
                Map.of("role", "user", "content", fullPrompt)
        });
        request.put("max_tokens", maxTokens);
        request.put("temperature", temperature);
        
        String response = client.post()
                .uri("/chat/completions")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .map(resp -> {
                    Map<String, Object> choices = ((java.util.List<Map<String, Object>>) resp.get("choices")).get(0);
                    Map<String, Object> message = (Map<String, Object>) choices.get("message");
                    return (String) message.get("content");
                })
                .block();
        
        return response;
    }

    /**
     * GPT OSS API 호출
     */
    private String callGptOss(Map<String, String> config, String sourceCode,
                              String conversionRules, String structureRules, String prompt) {
        String apiUrl = config.get("GPT_OSS_API_URL");
        String apiKey = config.get("GPT_OSS_API_KEY");
        String model = config.get("GPT_OSS_MODEL_NAME");
        int maxTokens = Integer.parseInt(config.getOrDefault("GPT_OSS_MAX_TOKENS", "8192"));
        double temperature = Double.parseDouble(config.getOrDefault("GPT_OSS_TEMPERATURE", "0.1"));
        
        WebClient client = WebClient.builder()
                .baseUrl(apiUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .build();
        
        String fullPrompt = buildPrompt(sourceCode, conversionRules, structureRules, prompt);
        
        Map<String, Object> request = new HashMap<>();
        request.put("model", model);
        request.put("messages", new Object[]{
                Map.of("role", "user", "content", fullPrompt)
        });
        request.put("max_tokens", maxTokens);
        request.put("temperature", temperature);
        
        String response = client.post()
                .uri("/chat/completions")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .map(resp -> {
                    Map<String, Object> choices = ((java.util.List<Map<String, Object>>) resp.get("choices")).get(0);
                    Map<String, Object> message = (Map<String, Object>) choices.get("message");
                    return (String) message.get("content");
                })
                .block();
        
        return response;
    }

    /**
     * LLM 프롬프트 구성
     */
    private String buildPrompt(String sourceCode, String conversionRules, 
                               String structureRules, String customPrompt) {
        return String.format("""
                당신은 C 코드를 Spring Boot 3.2.5 Java 코드로 변환하는 전문가입니다.
                
                [변환 규칙]
                %s
                
                [프로젝트 구조 규칙]
                %s
                
                [원본 C 코드]
                ```c
                %s
                ```
                
                [요청사항]
                %s
                
                [중요 지침]
                1. 규칙에 정의된 방식으로만 변환하세요
                2. 불확실한 부분은 [C2JAVA-REVIEW] 태그와 함께 원본을 주석으로 보존하세요
                3. SQL은 100%% 원본 그대로 유지하세요
                4. 모든 변환에 원본 정보를 주석으로 명시하세요
                5. 유추하거나 거짓으로 변환하지 마세요
                
                변환된 Java 코드를 생성하세요.
                """, 
                conversionRules, 
                structureRules, 
                sourceCode, 
                customPrompt);
    }
}

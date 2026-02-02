package com.c2java.service;

import com.c2java.config.LlmProperties;
import com.c2java.domain.ConversionJob;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * LLM 서비스
 * 사내 LLM (QWEN3 VL, GPT OSS)과 연동하여 코드 변환을 수행합니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LlmService {

    private final LlmProperties llmProperties;
    private final ObjectMapper objectMapper;
    
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(300, TimeUnit.SECONDS)  // LLM 응답은 시간이 걸릴 수 있음
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    /**
     * C 코드를 Java(Spring Boot)로 변환
     */
    public String convertToJava(ConversionJob job) throws IOException {
        String cCode = Files.readString(Path.of(job.getSourcePath()));
        
        // 변환 프롬프트 생성
        String prompt = buildConversionPrompt(cCode);
        
        // LLM 호출
        String javaCode = callLlm(prompt, job.getLlmProvider());
        
        // 결과 저장
        return saveConvertedCode(job, javaCode);
    }

    /**
     * 변환 프롬프트 생성
     */
    private String buildConversionPrompt(String cCode) {
        return """
            You are an expert software engineer specializing in converting C code to Java Spring Boot applications.
            
            ## Task
            Convert the following C code to Java Spring Boot 3.2.5 code.
            
            ## Requirements
            1. Use Java 21 LTS features where appropriate
            2. Follow Spring Boot 3.2.5 best practices
            3. Use proper package structure (com.company.converted)
            4. Include necessary imports
            5. Convert C structs to Java classes with Lombok annotations (@Data, @Builder)
            6. Convert C functions to appropriate Java methods
            7. Handle memory management (C malloc/free) appropriately for Java
            8. Convert C standard library calls to Java equivalents
            9. Add proper exception handling
            10. Include Javadoc comments
            
            ## C Code to Convert
            ```c
            %s
            ```
            
            ## Output Format
            Provide the complete Java code with proper package declaration, imports, and class structure.
            Use markdown code blocks for the Java code.
            """.formatted(cCode);
    }

    /**
     * LLM API 호출
     */
    private String callLlm(String prompt, String provider) throws IOException {
        LlmProperties.LlmConfig config = getConfigForProvider(provider);
        
        // OpenAI 호환 API 형식 사용
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", config.getModelName());
        requestBody.put("messages", List.of(
                Map.of("role", "system", "content", "You are an expert code converter specializing in C to Java conversion."),
                Map.of("role", "user", "content", prompt)
        ));
        requestBody.put("max_tokens", config.getMaxTokens());
        requestBody.put("temperature", config.getTemperature());
        
        String jsonBody = objectMapper.writeValueAsString(requestBody);
        
        Request request = new Request.Builder()
                .url(config.getApiUrl() + "/chat/completions")
                .addHeader("Authorization", "Bearer " + config.getApiKey())
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(jsonBody, MediaType.parse("application/json")))
                .build();
        
        log.info("Calling LLM API: {} with model: {}", config.getApiUrl(), config.getModelName());
        
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("LLM API call failed: " + response.code() + " - " + response.message());
            }
            
            String responseBody = response.body().string();
            JsonNode jsonNode = objectMapper.readTree(responseBody);
            
            return jsonNode
                    .path("choices")
                    .path(0)
                    .path("message")
                    .path("content")
                    .asText();
        }
    }

    /**
     * Provider에 따른 설정 반환
     */
    private LlmProperties.LlmConfig getConfigForProvider(String provider) {
        if (provider == null || provider.isBlank()) {
            provider = llmProperties.getActiveProvider();
        }
        
        return switch (provider.toLowerCase()) {
            case "qwen3" -> llmProperties.getQwen3();
            case "gpt_oss", "gpt-oss" -> llmProperties.getGptOss();
            default -> throw new IllegalArgumentException("Unknown LLM provider: " + provider);
        };
    }

    /**
     * 변환된 코드 저장
     */
    private String saveConvertedCode(ConversionJob job, String javaCode) throws IOException {
        String outputDir = System.getProperty("conversion.output-dir", "/app/output");
        Path outputPath = Paths.get(outputDir, job.getId().toString());
        Files.createDirectories(outputPath);
        
        // Java 코드 추출 (마크다운 코드 블록에서)
        String cleanCode = extractJavaCode(javaCode);
        
        // 메인 Java 파일 저장
        Path javaFilePath = outputPath.resolve("Application.java");
        Files.writeString(javaFilePath, cleanCode);
        
        // 전체 응답도 저장 (디버깅용)
        Path responseFilePath = outputPath.resolve("llm_response.txt");
        Files.writeString(responseFilePath, javaCode);
        
        return outputPath.toString();
    }

    /**
     * 마크다운 코드 블록에서 Java 코드 추출
     */
    private String extractJavaCode(String response) {
        // ```java ... ``` 형식에서 코드 추출
        int startIdx = response.indexOf("```java");
        if (startIdx == -1) {
            startIdx = response.indexOf("```");
        }
        
        if (startIdx != -1) {
            int codeStart = response.indexOf("\n", startIdx) + 1;
            int codeEnd = response.indexOf("```", codeStart);
            if (codeEnd != -1) {
                return response.substring(codeStart, codeEnd).trim();
            }
        }
        
        // 코드 블록이 없으면 전체 응답 반환
        return response;
    }
}

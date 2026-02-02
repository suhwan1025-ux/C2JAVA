package com.c2java.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * LLM 설정 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LlmConfigDto {
    
    private String activeProvider;
    private ProviderConfig qwen3;
    private ProviderConfig gptOss;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProviderConfig {
        private String name;
        private String apiUrl;
        private String apiKey;
        private String modelName;
        private int maxTokens;
        private double temperature;
    }
}

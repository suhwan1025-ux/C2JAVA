package com.c2java.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * LLM 설정 프로퍼티
 */
@Configuration
@ConfigurationProperties(prefix = "llm")
@Getter
@Setter
public class LlmProperties {

    /**
     * 활성 LLM 제공자 (qwen3 | gpt_oss)
     */
    private String activeProvider = "qwen3";

    /**
     * QWEN3 VL 설정
     */
    private LlmConfig qwen3 = new LlmConfig();

    /**
     * GPT OSS 설정
     */
    private LlmConfig gptOss = new LlmConfig();

    /**
     * 개별 LLM 설정
     */
    @Getter
    @Setter
    public static class LlmConfig {
        private String apiUrl;
        private String apiKey;
        private String modelName;
        private int maxTokens = 8192;
        private double temperature = 0.1;
    }
}

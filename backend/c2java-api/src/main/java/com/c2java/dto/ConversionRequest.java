package com.c2java.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 변환 요청 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversionRequest {
    
    /**
     * 작업 이름 (선택)
     */
    private String jobName;
    
    /**
     * 변환 대상 언어 (예: "springboot-3.2.5")
     */
    private String targetLanguage;
    
    /**
     * LLM 제공자 (qwen3 | gpt_oss)
     */
    private String llmProvider;
    
    /**
     * JDBC 연결 정보 (선택)
     */
    private String jdbcUrl;
    private String jdbcUser;
    private String jdbcPassword;
    private String jdbcDriver;
}

package com.c2java.service;

import com.c2java.domain.AppConfig;
import com.c2java.repository.AppConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 설정 관리 서비스
 * 런타임에 환경변수/설정을 동적으로 변경 가능
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ConfigService {

    private final AppConfigRepository configRepository;

    /**
     * 모든 설정 조회
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getAllConfigs() {
        return configRepository.findAllByOrderByCategoryAscConfigKeyAsc().stream()
                .map(this::configToMap)
                .collect(Collectors.toList());
    }

    /**
     * 카테고리별 설정 조회
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getConfigsByCategory(String category) {
        return configRepository.findByCategoryOrderByConfigKeyAsc(category).stream()
                .map(this::configToMap)
                .collect(Collectors.toList());
    }

    /**
     * 개별 설정 조회
     */
    @Transactional(readOnly = true)
    public Optional<String> getConfigValue(String key) {
        return configRepository.findByConfigKey(key)
                .map(AppConfig::getConfigValue);
    }

    /**
     * 설정 업데이트
     */
    @Transactional
    public Map<String, Object> updateConfig(String key, String value) {
        AppConfig config = configRepository.findByConfigKey(key)
                .orElseThrow(() -> new IllegalArgumentException("Config not found: " + key));
        
        if (!config.getIsEditable()) {
            throw new IllegalStateException("Config is not editable: " + key);
        }
        
        config.setConfigValue(value);
        configRepository.save(config);
        
        log.info("Config updated: {} = {}", key, 
                config.getIsSecret() ? "****" : value);
        
        return configToMap(config);
    }

    /**
     * 설정을 Map으로 변환 (민감정보 마스킹)
     */
    private Map<String, Object> configToMap(AppConfig config) {
        Map<String, Object> map = new HashMap<>();
        map.put("key", config.getConfigKey());
        map.put("value", config.getIsSecret() ? maskValue(config.getConfigValue()) : config.getConfigValue());
        map.put("category", config.getCategory());
        map.put("description", config.getDescription());
        map.put("isSecret", config.getIsSecret());
        map.put("isEditable", config.getIsEditable());
        map.put("updatedAt", config.getUpdatedAt());
        return map;
    }

    /**
     * 민감정보 마스킹
     */
    private String maskValue(String value) {
        if (value == null || value.length() < 8) {
            return "****";
        }
        return value.substring(0, 4) + "****" + value.substring(value.length() - 4);
    }
}

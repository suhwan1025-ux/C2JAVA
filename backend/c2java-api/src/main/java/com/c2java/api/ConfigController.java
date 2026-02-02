package com.c2java.api;

import com.c2java.service.ConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 환경설정 관리 API
 * 배포 후 런타임에 설정 변경 가능
 */
@RestController
@RequestMapping("/v1/configs")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Configuration", description = "환경설정 관리 API")
public class ConfigController {

    private final ConfigService configService;

    /**
     * 전체 설정 조회
     */
    @GetMapping
    @Operation(summary = "전체 설정 조회", description = "모든 환경설정을 조회합니다.")
    public ResponseEntity<List<Map<String, Object>>> getAllConfigs() {
        return ResponseEntity.ok(configService.getAllConfigs());
    }

    /**
     * 카테고리별 설정 조회
     */
    @GetMapping("/category/{category}")
    @Operation(summary = "카테고리별 설정 조회", description = "특정 카테고리의 설정을 조회합니다.")
    public ResponseEntity<List<Map<String, Object>>> getConfigsByCategory(
            @PathVariable String category) {
        return ResponseEntity.ok(configService.getConfigsByCategory(category.toUpperCase()));
    }

    /**
     * 개별 설정 조회
     */
    @GetMapping("/{key}")
    @Operation(summary = "개별 설정 조회", description = "특정 설정 값을 조회합니다.")
    public ResponseEntity<Map<String, Object>> getConfig(@PathVariable String key) {
        return configService.getConfigValue(key)
                .map(value -> ResponseEntity.ok(Map.of("key", (Object) key, "value", value)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 설정 업데이트
     */
    @PutMapping("/{key}")
    @Operation(summary = "설정 업데이트", description = "특정 설정 값을 변경합니다.")
    public ResponseEntity<Map<String, Object>> updateConfig(
            @PathVariable String key,
            @RequestBody Map<String, String> request) {
        
        String value = request.get("value");
        Map<String, Object> result = configService.updateConfig(key, value);
        return ResponseEntity.ok(result);
    }
}

package com.c2java.api;

import com.c2java.dto.LlmConfigDto;
import com.c2java.dto.SystemStatusDto;
import com.c2java.service.AdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 관리자 API 컨트롤러
 * LLM 설정, 시스템 상태 관리 등을 담당합니다.
 */
@RestController
@RequestMapping("/v1/admin")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin", description = "관리자 설정 API")
public class AdminController {

    private final AdminService adminService;

    /**
     * 시스템 상태 조회
     */
    @GetMapping("/status")
    @Operation(summary = "시스템 상태 조회", description = "전체 시스템 상태를 조회합니다.")
    public ResponseEntity<SystemStatusDto> getSystemStatus() {
        return ResponseEntity.ok(adminService.getSystemStatus());
    }

    /**
     * 현재 LLM 설정 조회
     */
    @GetMapping("/llm/config")
    @Operation(summary = "LLM 설정 조회", description = "현재 LLM 설정을 조회합니다.")
    public ResponseEntity<LlmConfigDto> getLlmConfig() {
        return ResponseEntity.ok(adminService.getLlmConfig());
    }

    /**
     * LLM 제공자 변경
     */
    @PutMapping("/llm/provider")
    @Operation(summary = "LLM 제공자 변경", description = "활성 LLM 제공자를 변경합니다.")
    public ResponseEntity<LlmConfigDto> changeLlmProvider(
            @RequestParam String provider) {
        return ResponseEntity.ok(adminService.changeLlmProvider(provider));
    }

    /**
     * LLM 설정 업데이트
     */
    @PutMapping("/llm/config")
    @Operation(summary = "LLM 설정 업데이트", description = "LLM 설정을 업데이트합니다.")
    public ResponseEntity<LlmConfigDto> updateLlmConfig(
            @RequestBody LlmConfigDto config) {
        return ResponseEntity.ok(adminService.updateLlmConfig(config));
    }

    /**
     * 환경변수 조회 (민감정보 마스킹)
     */
    @GetMapping("/env")
    @Operation(summary = "환경변수 조회", description = "시스템 환경변수를 조회합니다 (민감정보 마스킹).")
    public ResponseEntity<Map<String, String>> getEnvironmentVariables() {
        return ResponseEntity.ok(adminService.getEnvironmentVariables());
    }

    /**
     * CLI 도구 상태 확인
     */
    @GetMapping("/cli/status")
    @Operation(summary = "CLI 도구 상태", description = "AIDER, Fabric 등 CLI 도구의 상태를 확인합니다.")
    public ResponseEntity<Map<String, Object>> getCliStatus() {
        return ResponseEntity.ok(adminService.getCliStatus());
    }

    /**
     * 통계 정보 조회
     */
    @GetMapping("/statistics")
    @Operation(summary = "변환 통계", description = "변환 작업 통계를 조회합니다.")
    public ResponseEntity<Map<String, Object>> getStatistics() {
        return ResponseEntity.ok(adminService.getStatistics());
    }
}

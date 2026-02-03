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

    // ========== 환경변수 파일 동기화 API ==========

    /**
     * 환경변수 파일 정보 조회
     */
    @GetMapping("/env/file/info")
    @Operation(summary = "환경변수 파일 정보", description = "환경변수 파일 경로 및 존재 여부를 확인합니다.")
    public ResponseEntity<Map<String, Object>> getEnvFileInfo() {
        return ResponseEntity.ok(adminService.getEnvFileInfo());
    }

    /**
     * 환경변수 파일에서 LLM 설정 읽기
     */
    @GetMapping("/env/llm")
    @Operation(summary = "환경변수 파일 LLM 설정 읽기", description = "환경변수 파일에서 LLM 관련 설정을 읽어옵니다.")
    public ResponseEntity<Map<String, String>> getLlmEnvVariables() {
        return ResponseEntity.ok(adminService.getLlmEnvVariables());
    }

    /**
     * 환경변수 파일에 LLM 설정 저장
     */
    @PutMapping("/env/llm")
    @Operation(summary = "환경변수 파일 LLM 설정 저장", description = "환경변수 파일에 LLM 관련 설정을 저장합니다.")
    public ResponseEntity<Map<String, Object>> saveLlmEnvVariables(
            @RequestBody Map<String, String> envVars) {
        adminService.saveLlmEnvVariables(envVars);
        return ResponseEntity.ok(Map.of("success", true, "message", "LLM 환경변수가 저장되었습니다."));
    }

    /**
     * 환경변수 파일에서 CLI 설정 읽기
     */
    @GetMapping("/env/cli")
    @Operation(summary = "환경변수 파일 CLI 설정 읽기", description = "환경변수 파일에서 CLI 관련 설정을 읽어옵니다.")
    public ResponseEntity<Map<String, String>> getCliEnvVariables() {
        return ResponseEntity.ok(adminService.getCliEnvVariables());
    }

    /**
     * 환경변수 파일에 CLI 설정 저장
     */
    @PutMapping("/env/cli")
    @Operation(summary = "환경변수 파일 CLI 설정 저장", description = "환경변수 파일에 CLI 관련 설정을 저장합니다.")
    public ResponseEntity<Map<String, Object>> saveCliEnvVariables(
            @RequestBody Map<String, String> envVars) {
        adminService.saveCliEnvVariables(envVars);
        return ResponseEntity.ok(Map.of("success", true, "message", "CLI 환경변수가 저장되었습니다."));
    }

    // ========== 사용자 통계 API ==========

    /**
     * 사용자 통계 조회
     */
    @GetMapping("/users/stats")
    @Operation(summary = "사용자 통계", description = "사용자 현황 통계를 조회합니다.")
    public ResponseEntity<Map<String, Object>> getUserStats() {
        return ResponseEntity.ok(adminService.getUserStats());
    }

    // ========== 파일 서버 API ==========

    /**
     * 파일 서버 상태 조회
     */
    @GetMapping("/file-server/status")
    @Operation(summary = "파일 서버 상태", description = "파일 서버 설정 및 연결 상태를 조회합니다.")
    public ResponseEntity<Map<String, Object>> getFileServerStatus() {
        return ResponseEntity.ok(adminService.getFileServerStatus());
    }

    /**
     * 파일 서버 연결 테스트
     */
    @PostMapping("/file-server/test")
    @Operation(summary = "파일 서버 연결 테스트", description = "파일 서버 연결 상태를 테스트합니다.")
    public ResponseEntity<Map<String, Object>> testFileServerConnection() {
        return ResponseEntity.ok(adminService.testFileServerConnection());
    }
}

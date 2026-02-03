package com.c2java.api;

import com.c2java.service.RulesService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 변환 규칙 관리 API
 * 언어별 C to Java 변환 규칙 파일을 관리합니다.
 */
@RestController
@RequestMapping("/v1/rules")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Rules", description = "변환 규칙 관리 API")
public class RulesController {

    private final RulesService rulesService;

    /**
     * 언어 목록 조회 (관리자용)
     */
    @GetMapping("/languages")
    @Operation(summary = "언어 목록", description = "등록된 모든 언어 목록을 조회합니다.")
    public ResponseEntity<List<Map<String, Object>>> listLanguages() throws IOException {
        return ResponseEntity.ok(rulesService.listLanguages());
    }

    /**
     * 사용 가능한 언어 목록 조회 (사용자용 - 간단한 정보만)
     */
    @GetMapping("/languages/available")
    @Operation(summary = "사용 가능한 언어 목록", description = "변환에 사용할 수 있는 언어 목록을 조회합니다.")
    public ResponseEntity<List<Map<String, String>>> listAvailableLanguages() throws IOException {
        return ResponseEntity.ok(rulesService.listAvailableLanguages());
    }

    /**
     * 언어 상세 정보 조회
     */
    @GetMapping("/languages/{languageName}")
    @Operation(summary = "언어 상세 조회", description = "특정 언어의 규칙 파일 내용을 조회합니다.")
    public ResponseEntity<Map<String, Object>> getLanguageDetail(
            @PathVariable String languageName) throws IOException {
        return ResponseEntity.ok(rulesService.getLanguageDetail(languageName));
    }

    /**
     * 새 언어 생성
     */
    @PostMapping("/languages")
    @Operation(summary = "언어 생성", description = "새로운 언어를 생성하고 기본 템플릿을 추가합니다.")
    public ResponseEntity<Map<String, Object>> createLanguage(
            @RequestBody Map<String, String> request) throws IOException {
        String languageName = request.get("name");
        if (languageName == null || languageName.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(rulesService.createLanguage(languageName));
    }

    /**
     * 언어 삭제
     */
    @DeleteMapping("/languages/{languageName}")
    @Operation(summary = "언어 삭제", description = "언어와 관련 규칙 파일을 삭제합니다.")
    public ResponseEntity<Map<String, Object>> deleteLanguage(
            @PathVariable String languageName) throws IOException {
        rulesService.deleteLanguage(languageName);
        return ResponseEntity.ok(Map.of("success", true, "message", "언어가 삭제되었습니다."));
    }

    /**
     * 변환 규칙 파일 저장
     */
    @PutMapping("/languages/{languageName}/conversion-rules")
    @Operation(summary = "변환 규칙 저장", description = "변환 규칙 파일 내용을 저장합니다.")
    public ResponseEntity<Map<String, Object>> saveConversionRules(
            @PathVariable String languageName,
            @RequestBody Map<String, String> request) throws IOException {
        String content = request.get("content");
        if (content == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(rulesService.saveConversionRules(languageName, content));
    }

    /**
     * 프로젝트 구조 파일 저장
     */
    @PutMapping("/languages/{languageName}/project-structure")
    @Operation(summary = "프로젝트 구조 저장", description = "프로젝트 구조 파일 내용을 저장합니다.")
    public ResponseEntity<Map<String, Object>> saveProjectStructure(
            @PathVariable String languageName,
            @RequestBody Map<String, String> request) throws IOException {
        String content = request.get("content");
        if (content == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(rulesService.saveProjectStructure(languageName, content));
    }

    /**
     * 변환 규칙 파일 업로드
     */
    @PostMapping(value = "/languages/{languageName}/conversion-rules/upload", 
                 consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "변환 규칙 업로드", description = "변환 규칙 파일을 업로드합니다.")
    public ResponseEntity<Map<String, Object>> uploadConversionRules(
            @PathVariable String languageName,
            @RequestPart("file") MultipartFile file) throws IOException {
        return ResponseEntity.ok(rulesService.uploadConversionRules(languageName, file));
    }

    /**
     * 프로젝트 구조 파일 업로드
     */
    @PostMapping(value = "/languages/{languageName}/project-structure/upload", 
                 consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "프로젝트 구조 업로드", description = "프로젝트 구조 파일을 업로드합니다.")
    public ResponseEntity<Map<String, Object>> uploadProjectStructure(
            @PathVariable String languageName,
            @RequestPart("file") MultipartFile file) throws IOException {
        return ResponseEntity.ok(rulesService.uploadProjectStructure(languageName, file));
    }
}

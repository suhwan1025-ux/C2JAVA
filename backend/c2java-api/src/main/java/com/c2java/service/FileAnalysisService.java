package com.c2java.service;

import com.c2java.domain.ConversionJob;
import com.c2java.domain.FileAnalysis;
import com.c2java.repository.FileAnalysisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 파일 분석 서비스
 * CLI 도구(Fabric)를 사용하여 C 파일 구조를 분석합니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FileAnalysisService {

    private final FileAnalysisRepository fileAnalysisRepository;
    private final CliService cliService;

    /**
     * C 파일 분석
     */
    public FileAnalysis analyzeFile(ConversionJob job) throws IOException {
        Path filePath = Path.of(job.getSourcePath());
        String content = Files.readString(filePath);
        List<String> lines = Files.readAllLines(filePath);
        
        // 기본 분석 수행
        Map<String, Object> analysisResult = new HashMap<>();
        
        int functionCount = countPattern(content, "\\w+\\s+\\w+\\s*\\([^)]*\\)\\s*\\{");
        int structCount = countPattern(content, "struct\\s+\\w+\\s*\\{");
        int includeCount = countPattern(content, "#include\\s*[<\"].*[\">]");
        
        // 복잡도 계산 (간단한 휴리스틱)
        double complexityScore = calculateComplexity(content, lines.size(), functionCount);
        
        analysisResult.put("functions", extractFunctions(content));
        analysisResult.put("structs", extractStructs(content));
        analysisResult.put("includes", extractIncludes(content));
        analysisResult.put("globalVariables", extractGlobalVariables(content));
        
        // CLI 도구로 추가 분석 (Fabric)
        try {
            String cliAnalysis = cliService.analyzeWithFabric(job.getSourcePath());
            analysisResult.put("cliAnalysis", cliAnalysis);
        } catch (Exception e) {
            log.warn("CLI analysis failed, using basic analysis: {}", e.getMessage());
        }
        
        // Map을 JSON 문자열로 변환
        String analysisJson;
        try {
            analysisJson = new ObjectMapper().writeValueAsString(analysisResult);
        } catch (Exception e) {
            analysisJson = "{}";
        }
        
        FileAnalysis analysis = FileAnalysis.builder()
                .job(job)
                .originalFilename(filePath.getFileName().toString())
                .fileType("C")
                .lineCount(lines.size())
                .functionCount(functionCount)
                .structCount(structCount)
                .includeCount(includeCount)
                .complexityScore(complexityScore)
                .analysisResult(analysisJson)
                .build();
        
        return fileAnalysisRepository.save(analysis);
    }

    /**
     * 패턴 개수 카운트
     */
    private int countPattern(String content, String regex) {
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(content);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    /**
     * 복잡도 점수 계산
     */
    private double calculateComplexity(String content, int lineCount, int functionCount) {
        // 간단한 복잡도 계산 (라인 수, 함수 수, 제어 구조 기반)
        int controlStructures = countPattern(content, "\\b(if|else|for|while|switch|case)\\b");
        int pointers = countPattern(content, "\\*\\w+|\\w+\\s*\\*");
        
        double score = (lineCount * 0.1) + (functionCount * 2) + (controlStructures * 1.5) + (pointers * 0.5);
        return Math.min(100, score);  // 최대 100점
    }

    /**
     * 함수 목록 추출
     */
    private List<String> extractFunctions(String content) {
        Pattern pattern = Pattern.compile("(\\w+)\\s+(\\w+)\\s*\\(([^)]*)\\)\\s*\\{");
        Matcher matcher = pattern.matcher(content);
        
        return matcher.results()
                .map(m -> m.group(1) + " " + m.group(2) + "(" + m.group(3) + ")")
                .toList();
    }

    /**
     * 구조체 목록 추출
     */
    private List<String> extractStructs(String content) {
        Pattern pattern = Pattern.compile("struct\\s+(\\w+)");
        Matcher matcher = pattern.matcher(content);
        
        return matcher.results()
                .map(m -> m.group(1))
                .toList();
    }

    /**
     * include 목록 추출
     */
    private List<String> extractIncludes(String content) {
        Pattern pattern = Pattern.compile("#include\\s*[<\"]([^>\"]+)[\">]");
        Matcher matcher = pattern.matcher(content);
        
        return matcher.results()
                .map(m -> m.group(1))
                .toList();
    }

    /**
     * 전역 변수 추출 (간단한 휴리스틱)
     */
    private List<String> extractGlobalVariables(String content) {
        // 함수 외부의 변수 선언 추출 (단순화된 버전)
        Pattern pattern = Pattern.compile("^(\\w+)\\s+(\\w+)\\s*;", Pattern.MULTILINE);
        Matcher matcher = pattern.matcher(content);
        
        return matcher.results()
                .map(m -> m.group(1) + " " + m.group(2))
                .toList();
    }
}

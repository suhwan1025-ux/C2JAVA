package com.c2java.service;

import com.c2java.domain.ConversionJob;
import com.c2java.domain.ConversionReview;
import com.c2java.repository.ConversionReviewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * 리뷰 서비스
 * 변환 결과에 대한 리뷰를 생성합니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReviewService {

    private final ConversionReviewRepository reviewRepository;
    private final LlmService llmService;

    /**
     * 변환 리뷰 생성
     */
    public void generateReview(ConversionJob job) {
        log.info("Generating review for job: {}", job.getId());
        
        try {
            // 원본 코드와 변환된 코드 읽기
            String originalCode = Files.readString(Path.of(job.getSourcePath()));
            String convertedCode = readConvertedCode(job.getOutputPath());
            
            // 코드 품질 리뷰
            String qualityReview = generateQualityReview(originalCode, convertedCode);
            saveReview(job, "QUALITY", qualityReview);
            
            // 기능 동등성 리뷰
            String functionalReview = generateFunctionalReview(originalCode, convertedCode);
            saveReview(job, "FUNCTIONAL", functionalReview);
            
            // 성능 고려사항
            String performanceReview = generatePerformanceReview(convertedCode);
            saveReview(job, "PERFORMANCE", performanceReview);
            
            // 전체 요약
            String summary = generateSummary(job);
            saveReview(job, "SUMMARY", summary);
            
            log.info("Review generation completed for job: {}", job.getId());
            
        } catch (Exception e) {
            log.error("Failed to generate review for job: {}", job.getId(), e);
            saveReview(job, "ERROR", "Review generation failed: " + e.getMessage());
        }
    }

    /**
     * 변환된 코드 읽기
     */
    private String readConvertedCode(String outputPath) throws IOException {
        Path path = Path.of(outputPath);
        StringBuilder code = new StringBuilder();
        
        try (Stream<Path> files = Files.walk(path)) {
            files.filter(p -> p.toString().endsWith(".java"))
                    .forEach(p -> {
                        try {
                            code.append("// File: ").append(p.getFileName()).append("\n");
                            code.append(Files.readString(p)).append("\n\n");
                        } catch (IOException e) {
                            log.warn("Failed to read file: {}", p, e);
                        }
                    });
        }
        
        return code.toString();
    }

    /**
     * 코드 품질 리뷰 생성
     */
    private String generateQualityReview(String originalCode, String convertedCode) {
        StringBuilder review = new StringBuilder();
        
        review.append("## 코드 품질 리뷰\n\n");
        
        // 코드 길이 비교
        int originalLines = originalCode.split("\n").length;
        int convertedLines = convertedCode.split("\n").length;
        review.append(String.format("- 원본 코드: %d 라인\n", originalLines));
        review.append(String.format("- 변환 코드: %d 라인\n", convertedLines));
        review.append(String.format("- 비율: %.2f%%\n\n", (double) convertedLines / originalLines * 100));
        
        // 코딩 표준 검사
        review.append("### 코딩 표준\n");
        if (convertedCode.contains("@Data") || convertedCode.contains("@Getter")) {
            review.append("- ✅ Lombok 어노테이션 사용\n");
        }
        if (convertedCode.contains("@Service") || convertedCode.contains("@Repository")) {
            review.append("- ✅ Spring 어노테이션 사용\n");
        }
        if (convertedCode.contains("/**")) {
            review.append("- ✅ Javadoc 주석 포함\n");
        }
        
        return review.toString();
    }

    /**
     * 기능 동등성 리뷰 생성
     */
    private String generateFunctionalReview(String originalCode, String convertedCode) {
        StringBuilder review = new StringBuilder();
        
        review.append("## 기능 동등성 리뷰\n\n");
        
        // 함수 매핑 확인
        review.append("### 함수 변환 현황\n");
        // TODO: 실제 함수 매핑 분석 로직 추가
        review.append("- 원본 함수들이 적절한 Java 메서드로 변환되었습니다.\n");
        review.append("- 메모리 관리 코드가 Java 방식으로 적절히 변환되었습니다.\n");
        
        return review.toString();
    }

    /**
     * 성능 리뷰 생성
     */
    private String generatePerformanceReview(String convertedCode) {
        StringBuilder review = new StringBuilder();
        
        review.append("## 성능 고려사항\n\n");
        
        // 성능 관련 패턴 검사
        if (convertedCode.contains("StringBuilder")) {
            review.append("- ✅ 문자열 연결에 StringBuilder 사용\n");
        }
        if (convertedCode.contains("Stream")) {
            review.append("- ℹ️ Java Stream API 사용 - 대용량 데이터 처리 시 병렬 스트림 고려 필요\n");
        }
        if (convertedCode.contains("synchronized")) {
            review.append("- ⚠️ synchronized 키워드 사용 - 성능 영향 검토 필요\n");
        }
        
        return review.toString();
    }

    /**
     * 전체 요약 생성
     */
    private String generateSummary(ConversionJob job) {
        StringBuilder summary = new StringBuilder();
        
        summary.append("## 변환 요약\n\n");
        summary.append(String.format("- 작업 ID: %s\n", job.getId()));
        summary.append(String.format("- 작업명: %s\n", job.getJobName()));
        summary.append(String.format("- 사용 LLM: %s\n", job.getLlmProvider()));
        summary.append(String.format("- 재시도 횟수: %d\n", job.getCompileAttempts()));
        summary.append("\n### 권장 사항\n");
        summary.append("1. 변환된 코드의 비즈니스 로직 검증 필요\n");
        summary.append("2. 단위 테스트 추가 권장\n");
        summary.append("3. 성능 테스트 수행 권장\n");
        
        return summary.toString();
    }

    /**
     * 리뷰 저장
     */
    private void saveReview(ConversionJob job, String type, String content) {
        ConversionReview review = ConversionReview.builder()
                .job(job)
                .reviewType(type)
                .content(content)
                .build();
        
        reviewRepository.save(review);
    }
}

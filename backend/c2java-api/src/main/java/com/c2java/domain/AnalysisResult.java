package com.c2java.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * C 파일 분석 결과
 */
@Entity
@Table(name = "analysis_results")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_id", nullable = false)
    private String jobId;

    @Column(name = "source_file", nullable = false)
    private String sourceFile;

    @Column(name = "file_type")
    private String fileType; // c_source, c_header, pro_c

    // 분석된 요소들
    @Column(name = "functions")
    @Lob
    private String functions; // JSON 배열

    @Column(name = "structs")
    @Lob
    private String structs; // JSON 배열

    @Column(name = "enums")
    @Lob
    private String enums; // JSON 배열

    @Column(name = "sql_queries")
    @Lob
    private String sqlQueries; // JSON 배열

    @Column(name = "includes")
    @Lob
    private String includes; // JSON 배열

    @Column(name = "defines")
    @Lob
    private String defines; // JSON 배열

    @Column(name = "global_variables")
    @Lob
    private String globalVariables; // JSON 배열

    // 통계
    @Column(name = "line_count")
    private Integer lineCount;

    @Column(name = "function_count")
    private Integer functionCount;

    @Column(name = "struct_count")
    private Integer structCount;

    @Column(name = "complexity_score")
    private Integer complexityScore;
}

package com.c2java.repository;

import com.c2java.domain.AnalysisResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 분석 결과 Repository
 */
@Repository
public interface AnalysisResultRepository extends JpaRepository<AnalysisResult, Long> {
    
    List<AnalysisResult> findByJobId(String jobId);
    
    void deleteByJobId(String jobId);
}

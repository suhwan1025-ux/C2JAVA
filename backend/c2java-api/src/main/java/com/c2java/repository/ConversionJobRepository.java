package com.c2java.repository;

import com.c2java.domain.ConversionJob;
import com.c2java.domain.ConversionJob.JobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * 변환 작업 리포지토리
 */
@Repository
public interface ConversionJobRepository extends JpaRepository<ConversionJob, UUID> {
    
    /**
     * 생성일 역순으로 모든 작업 조회
     */
    List<ConversionJob> findAllByOrderByCreatedAtDesc();
    
    /**
     * 상태별 작업 조회
     */
    List<ConversionJob> findByStatusOrderByCreatedAtDesc(JobStatus status);
    
    /**
     * 특정 상태의 작업 개수
     */
    long countByStatus(JobStatus status);
}

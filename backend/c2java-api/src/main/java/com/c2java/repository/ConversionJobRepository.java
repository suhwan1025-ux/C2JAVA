package com.c2java.repository;

import com.c2java.domain.ConversionJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 변환 작업 Repository
 */
@Repository
public interface ConversionJobRepository extends JpaRepository<ConversionJob, UUID> {
    
    List<ConversionJob> findByUserIdOrderByCreatedAtDesc(UUID userId);
    
    List<ConversionJob> findByStatusOrderByCreatedAtDesc(ConversionJob.JobStatus status);
    
    List<ConversionJob> findTop20ByOrderByCreatedAtDesc();
    
    List<ConversionJob> findAllByOrderByCreatedAtDesc();
    
    Optional<ConversionJob> findByJobId(UUID jobId);
    
    long countByStatus(ConversionJob.JobStatus status);
}

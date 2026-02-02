package com.c2java.repository;

import com.c2java.domain.ConversionReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ConversionReviewRepository extends JpaRepository<ConversionReview, UUID> {
    List<ConversionReview> findByJobId(UUID jobId);
    List<ConversionReview> findByJobIdAndReviewType(UUID jobId, String reviewType);
}

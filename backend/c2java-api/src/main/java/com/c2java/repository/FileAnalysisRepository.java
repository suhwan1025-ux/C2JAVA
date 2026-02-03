package com.c2java.repository;

import com.c2java.domain.FileAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface FileAnalysisRepository extends JpaRepository<FileAnalysis, UUID> {
    List<FileAnalysis> findByJobId(String jobId);
}

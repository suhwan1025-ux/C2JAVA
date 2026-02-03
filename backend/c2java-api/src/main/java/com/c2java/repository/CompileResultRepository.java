package com.c2java.repository;

import com.c2java.domain.CompileResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CompileResultRepository extends JpaRepository<CompileResult, UUID> {
    List<CompileResult> findByJobIdOrderByAttemptNumberDesc(String jobId);
}

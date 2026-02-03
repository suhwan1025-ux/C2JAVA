package com.c2java.repository;

import com.c2java.domain.RuntimeResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RuntimeResultRepository extends JpaRepository<RuntimeResult, UUID> {
    List<RuntimeResult> findByJobId(UUID jobId);
}

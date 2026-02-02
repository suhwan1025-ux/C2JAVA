package com.c2java.repository;

import com.c2java.domain.AppConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AppConfigRepository extends JpaRepository<AppConfig, UUID> {
    Optional<AppConfig> findByConfigKey(String configKey);
    List<AppConfig> findByCategory(String category);
    List<AppConfig> findByCategoryOrderByConfigKeyAsc(String category);
    List<AppConfig> findAllByOrderByCategoryAscConfigKeyAsc();
    boolean existsByConfigKey(String configKey);
}

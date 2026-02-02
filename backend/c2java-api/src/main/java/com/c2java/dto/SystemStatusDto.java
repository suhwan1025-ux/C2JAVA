package com.c2java.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 시스템 상태 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemStatusDto {
    
    private String status;
    private long uptime;
    private long heapMemoryUsed;
    private long heapMemoryMax;
    private String activeLlmProvider;
    private boolean aiderEnabled;
    private boolean fabricEnabled;
    private long pendingJobs;
    private long runningJobs;
    private long completedJobs;
    private long failedJobs;
}

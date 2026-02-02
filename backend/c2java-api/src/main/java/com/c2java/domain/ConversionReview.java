package com.c2java.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 변환 리뷰 엔티티
 */
@Entity
@Table(name = "conversion_reviews")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversionReview {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id")
    private ConversionJob job;

    @Column(name = "review_type", nullable = false)
    private String reviewType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column
    private Double score;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}

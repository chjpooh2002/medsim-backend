package com.medsim.backend.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "region_rents")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class RegionRent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String district;

    @Column(nullable = false, length = 30)
    private String dong;

    @Column(nullable = false, length = 30)
    private String specialty;

    /** 평당 평균 임대료 (만원) */
    private Integer avgRentPerPyeong;

    /** 월 최소 임대료 (만원) */
    private Integer minRent;

    /** 월 최대 임대료 (만원) */
    private Integer maxRent;

    /** 선호 층수 */
    private Integer floor;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}

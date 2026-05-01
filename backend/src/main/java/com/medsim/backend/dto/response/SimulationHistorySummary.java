package com.medsim.backend.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class SimulationHistorySummary {
    private Long id;
    private String deptCategory;
    private String regionSiGun;
    private Integer bepMonth;
    private Double fixedCostRatio;
    private Long finalCashBalance;
    private LocalDateTime createdAt;
}

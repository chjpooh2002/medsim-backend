package com.medsim.backend.dto.response;

import com.medsim.backend.domain.MonthlyData;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SimulationResult {

    // KPI 요약
    private Integer bepMonth;
    private Double  fixedCostRatio;
    private Long    finalCashBalance;
    private Long    bepTargetRevenue;   // BEP 달성 최소 목표 월 매출액 (만원)
    private Double  patientGrowthRate;
    private Long    marketingCpa;       // 신규 환자 1명 유치 평균 비용 (만원/명)

    // 36개월 그래프용
    private List<Long> cumulativeRevenue;
    private List<Long> cumulativeCost;

    // 상세 손익계산서 + 현금흐름표
    private List<MonthlyData> monthly;

    // KPI 피드백
    private List<KpiMessage> kpiMessages;
}
package com.medsim.backend.domain;

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
    private Integer bepMonth;           // 예상 BEP 달성 시점 (개월
    private Double  fixedCostRatio;     // 고정비 비율 (3년 평균, %)
    private Long    finalCashBalance;   // 3년차 기말 현금잔고 (만원)
    private Integer bepMonthBase;       // BEP 매출 달성 개월수
    private Double  patientGrowthRate;  // 평균 환자수 증가율 (%)
    private String  marketingCpa;       // 마케팅 효율 CPA (우수/보통/미흡)

    // 36개월 그래프용
    private List<Long> cumulativeRevenue;   // 누적 매출 (36개 값)
    private List<Long> cumulativeCost;      // 누적 비용 (36개 값)

    // 상세 손익계산서 + 현금흐름표
    private List<MonthlyData> monthly;

    // KPI 피드백 메시지
    private List<String> kpiMessages;
}
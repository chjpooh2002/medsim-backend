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

    // KPI 요약 (결과 대시보드 상단 6개 카드)
    private Integer bepMonth;           // 손익분기 달성 시점 (개월)
    private Double  fixedCostRatio;     // 고정비 비율 3년 평균 (%)
    private Long    finalCashBalance;   // 36개월 기말 현금잔고 (만원)
    private Long    bepTargetRevenue;   // BEP 달성을 위한 최소 월 매출 목표 (만원)
    private Double  patientGrowthRate;  // M1 대비 M36 환자수 증가율 (%)
    private Long    marketingCpa;       // 신규 환자 1명 유치 평균 비용 (만원/명)

    // 36개월 손익 교차 그래프용 데이터
    private List<Long> cumulativeRevenue;   // 누적 매출 (36개 값)
    private List<Long> cumulativeCost;      // 누적 비용 (36개 값)

    // 상세 손익계산서 + 현금흐름표
    private List<MonthlyData> monthly;

    // KPI 피드백 알림창 목록 (message + theme)
    private List<KpiMessage> kpiMessages;
}
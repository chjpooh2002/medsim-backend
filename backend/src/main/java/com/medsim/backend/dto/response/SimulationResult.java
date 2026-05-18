package com.medsim.backend.dto.response;

import com.medsim.backend.domain.MonthlyData;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.Map;



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

    // 비재무 최종 지표
    private Double finalReputation;          // 36개월 최종 평판 (0~5.0)
    private Double finalSatisfaction;        // 최종 환자 만족도 (0~5.0)
    private Double finalReturnRate;          // 최종 재진율 (0~1.0)
    private Map<String, Long> costBreakdown; // 비용 항목별 36개월 누적 합계
    private Boolean isBankrupt;              // 파산 여부 (현금잔고 0 이하 도달)
    private String grade;                    // 경영 등급 S/A/B/C/F
}
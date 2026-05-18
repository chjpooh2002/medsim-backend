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
public class MonthlyData {

    private Integer month;           // 몇 번째 달 (1~36)

    // 손익계산서
    private Long revenue;            // 매출액 (환자수 × 건당 수가)
    private Long variableCost;       // 변동비 (매출의 15% — 의약품비, 재료비 등)
    private Long fixedCost;          // 고정비 (인건비 + 임대료 + 감가상각 + 마케팅 + 기타관리비)
    private Long operatingProfit;    // 영업이익 = 매출 - 변동비 - 고정비
    private Long interestExpense;    // 이자비용 (원금 균등 상환, 매월 감소)
    private Long taxExpense;         // 세금 (영업이익 > 0일 때만, 고정 20%)
    private Long netProfit;          // 당기순이익 = 영업이익 - 이자 - 세금

    // 현금흐름표 (실제 통장 기준 — 감가상각 같은 비현금 항목 제외)
    private Long operatingCashFlow;  // 영업 현금흐름 (실제 들어오고 나간 돈)
    private Long investingCashFlow;  // 투자 현금흐름 (1개월차 초기투자금 지출만)
    private Long financingCashFlow;  // 재무 현금흐름 (대출 실행 및 원금 상환)
    private Long cumulativeCash;     // 누적 현금잔고 (마이너스면 자금 고갈 위험)

    // 비재무 지표
    private Double reputationScore;     // 병원 평판 0~5.0
    private Double patientSatisfaction; // 환자 만족도 0~5.0
    private Double returnPatientRate;   // 재진율 0~1.0
    private Double staffMorale;         // 직원 사기 0~1.0
    private List<String> activeEvents;  // 해당 월 발생 이벤트 ID 목록
}

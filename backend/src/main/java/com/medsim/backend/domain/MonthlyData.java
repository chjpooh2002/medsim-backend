package com.medsim.backend.domain;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MonthlyData {

    private Integer month;           // 월 (1~36)

    // 손익계산서 (P&L)
    private Long revenue;            // 매출액
    private Long variableCost;       // 변동비 (재료비 등)
    private Long fixedCost;          // 고정비 합계
    private Long operatingProfit;    // 영업이익
    private Long interestExpense;    // 이자비용
    private Long netProfit;          // 당기순이익

    // 현금흐름표
    private Long operatingCashFlow;  // 영업활동 현금흐름
    private Long investingCashFlow;  // 투자활동 현금흐름 (초기에만)
    private Long financingCashFlow;  // 재무활동 현금흐름 (대출상환)
    private Long cumulativeCash;     // 누적 현금잔고
}

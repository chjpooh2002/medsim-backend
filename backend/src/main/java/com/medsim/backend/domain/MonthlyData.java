package com.medsim.backend.domain;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MonthlyData {

    private Integer month;

    // 손익계산서 (P&L)
    private Long revenue;
    private Long variableCost;
    private Long fixedCost;
    private Long operatingProfit;
    private Long interestExpense;
    private Long taxExpense;        // 세금 (영업이익 × 20%) 신규
    private Long netProfit;

    // 현금흐름표
    private Long operatingCashFlow;
    private Long investingCashFlow;
    private Long financingCashFlow;
    private Long cumulativeCash;
}

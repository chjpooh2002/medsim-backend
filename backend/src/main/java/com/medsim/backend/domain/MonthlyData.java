package com.medsim.backend.domain;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MonthlyData {

    private int month;        // 몇 번째 달 (1 ~ 36)
    private long revenue;     // 매출 (원)
    private long fixedCost;   // 고정비 (원)
    private long profit;      // 순이익 (원) = 매출 - 고정비
    private long cashflow;    // 현금잔고 (원) = 누적 합산
}

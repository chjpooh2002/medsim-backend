package com.medsim.backend.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor  // 기본 생성자 추가
@AllArgsConstructor // 모든 필드 생성자 추가 (Builder가 내부적으로 필요로 함)
public class MonthlyData {

    private int month;        // 몇 번째 달 (1 ~ 36)
    private long revenue;     // 매출 (원)
    private long fixedCost;   // 고정비 (원)
    private long profit;      // 순이익 (원) = 매출 - 고정비
    private long cashflow;    // 현금잔고 (원) = 누적 합산
}

package com.medsim.backend.domain;

import lombok.Builder;
import lombok.Getter;
import java.util.List;

@Getter
@Builder
public class SimulationResult {

    private int bepMonth;              // BEP 도달 월 (손익분기점)
    private long totalProfit36;        // 3년 누적 순이익 (원)
    private long minCashflow;          // 36개월 중 최저 현금잔고 (원)
    private List<MonthlyData> monthly; // 36개월 상세 데이터
    private List<String> kpiMessages;  // KPI 진단 메시지 목록
}
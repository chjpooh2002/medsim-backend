package com.medsim.backend.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor  // 기본 생성자 추가
@AllArgsConstructor // 모든 필드 생성자 추가 (Builder가 내부적으로 필요로 함)
public class SimulationResult {

    private int bepMonth;              // BEP 도달 월 (손익분기점)
    private long totalProfit36;        // 3년 누적 순이익 (원)
    private long minCashflow;          // 36개월 중 최저 현금잔고 (원)
    private List<MonthlyData> monthly; // 36개월 상세 데이터
    private List<String> kpiMessages;  // KPI 진단 메시지 목록
}
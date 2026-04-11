package com.medsim.backend.service;

import com.medsim.backend.domain.MonthlyData;
import com.medsim.backend.domain.SimulationRequest;
import com.medsim.backend.domain.SimulationResult;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class SimulationService {

    // 서초/강남 내과 기준 고정값 (나중에 DB로 교체할 부분)
    private static final int BASE_PATIENTS = 30;    // 일 평균 환자수
    private static final int UNIT_PRICE    = 15000; // 객단가 (원)
    private static final int WORKING_DAYS  = 22;    // 월 진료일수

    public SimulationResult simulate(SimulationRequest req) {

        List<MonthlyData> monthlyList = new ArrayList<>();
        long cashflow = (long) req.getSelfCapital() * 10000; // 만원 → 원 변환

        int bepMonth = -1;          // BEP 아직 못 찾음
        long totalProfit = 0;
        long minCashflow = Long.MAX_VALUE;

        for (int month = 1; month <= 36; month++) {

            // 환자 유입 곡선 (초반엔 적다가 점점 늘어남, 최대 100%)
            double growth = Math.min(1.0, 0.4 + month * 0.05);

            // 매출 계산
            long revenue = (long)(BASE_PATIENTS * growth * UNIT_PRICE * WORKING_DAYS);

            // 고정비 계산
            long fixedCost = calcFixedCost(req);

            // 순이익
            long profit = revenue - fixedCost;

            // 현금잔고 누적
            cashflow += profit;

            // BEP 탐색 (처음으로 현금이 플러스 된 시점)
            if (bepMonth == -1 && cashflow > 0) {
                bepMonth = month;
            }

            // 최저 현금 갱신
            if (cashflow < minCashflow) {
                minCashflow = cashflow;
            }

            totalProfit += profit;

            monthlyList.add(MonthlyData.builder()
                    .month(month)
                    .revenue(revenue)
                    .fixedCost(fixedCost)
                    .profit(profit)
                    .cashflow(cashflow)
                    .build());
        }

        return SimulationResult.builder()
                .bepMonth(bepMonth)
                .totalProfit36(totalProfit)
                .minCashflow(minCashflow)
                .monthly(monthlyList)
                .kpiMessages(generateKpiMessages(bepMonth, minCashflow))
                .build();
    }


    // 고정비 계산 (임대료 + 인건비 + 마케팅)
    private long calcFixedCost(SimulationRequest req) {
        long staffCost = req.getStaffList().stream()
                .mapToLong(s -> (long) s.getMonthlySalary() * s.getCount() * 10000)
                .sum();

        long rent          = (long) req.getRent() * 10000;
        long marketingCost = (long) req.getMarketingBudget() * 10000;

        return rent + staffCost + marketingCost;
    }


    // KPI 진단 메시지 생성
    private List<String> generateKpiMessages(int bepMonth, long minCashflow) {
        List<String> messages = new ArrayList<>();

        if (minCashflow < 0) {
            messages.add("운전자본 부족 — 초기 현금 " + minCashflow / 10000 + "만원, 6개월치 고정비 확보 필요");
        }

        if (bepMonth == -1) {
            messages.add("36개월 내 BEP 미달성 — 비용 구조 재검토 필요");
        } else if (bepMonth <= 12) {
            messages.add("BEP " + bepMonth + "개월 달성 — 업종 평균(12개월)보다 빠름");
        } else {
            messages.add("BEP " + bepMonth + "개월 달성 — 업종 평균(12개월)보다 느림");
        }

        return messages;
    }
}

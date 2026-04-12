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
    private static final int BASE_PATIENTS = 40;    // 일 평균 환자수
    private static final int UNIT_PRICE    = 20000; // 객단가 (원)
    private static final int WORKING_DAYS  = 22;    // 월 진료일수

    public SimulationResult simulate(SimulationRequest req) {
        List<MonthlyData> monthlyList = new ArrayList<>();

        // 초기 자본금을 원 단위로 설정
        long initialCapital = (long) req.getSelfCapital() * 10000;
        long cashflow = initialCapital;

        int bepMonth = -1;
        long totalProfit = 0;

        // minCashflow의 초기값을 시작 자본금으로 설정
        long minCashflow = initialCapital;

        for (int month = 1; month <= 36; month++) {
            double growth = Math.min(1.0, 0.4 + month * 0.05);
            long revenue = (long)(BASE_PATIENTS * growth * UNIT_PRICE * WORKING_DAYS);
            long fixedCost = calcFixedCost(req);
            long profit = revenue - fixedCost;

            cashflow += profit;  // 실제 잔고 업데이트
            totalProfit += profit;

            // BEP: 월별 수익(profit)이 처음으로 플러스가 되는 달
            if (bepMonth == -1 && profit > 0) {
                bepMonth = month;
            }

            // 최저 현금 잔고 갱신 (자본금 포함 실제 잔고 기준)
            if (cashflow < minCashflow) {
                minCashflow = cashflow;
            }

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
                .minCashflow(minCashflow) // 이제 자본금 포함 최저 잔고가 나옵니다
                .monthly(monthlyList)
                .kpiMessages(generateKpiMessages(bepMonth, minCashflow, initialCapital)) // 파라미터 수정
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
    private List<String> generateKpiMessages(int bepMonth, long minCashflow, long initialCapital) {
        List<String> messages = new ArrayList<>();

        // 실제 잔고가 0 밑으로 내려가면 진짜 위험한 상황!
        if (minCashflow < 0) {
            messages.add("운전자본 고갈 위험 — 현재 구성으로는 초기 자본금이 부족합니다.");
        } else if (minCashflow < initialCapital * 0.2) {
            messages.add("현금 흐름 주의 — 자본금의 20% 미만으로 잔고가 떨어지는 시점이 있습니다.");
        }

        if (bepMonth == -1) {
            messages.add("36개월 내 월 단위 흑자 전환 미달성 — 비용 구조 재검토 필요");
        } else {
            messages.add("BEP " + bepMonth + "개월 달성 — " + (bepMonth <= 12 ? "매우 양호" : "평균 수준"));
        }

        return messages;
    }
}

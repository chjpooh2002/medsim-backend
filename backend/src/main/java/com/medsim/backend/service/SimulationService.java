package com.medsim.backend.service;

import com.medsim.backend.domain.MonthlyData;
import com.medsim.backend.domain.SimulationRequest;
import com.medsim.backend.domain.SimulationResult;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class SimulationService {

    private static final int    WORKING_DAYS      = 22;
    private static final double VARIABLE_COST_RATIO = 0.15; // 변동비 15%

    public SimulationResult simulate(SimulationRequest req) {

        // 기준값 산출
        int    basePatientsPerDay = getBasePatients(req.getDeptCategory());
        double areaFactor         = getAreaFactor(req.getRegionSiGun());
        long   fixedMonthly       = calcFixedCost(req);
        double monthlyInterest    = calcMonthlyInterest(req);

        List<MonthlyData> monthlyList     = new ArrayList<>();
        List<Long>        cumRevList      = new ArrayList<>();
        List<Long>        cumCostList     = new ArrayList<>();

        long cashBalance   = req.getInitialInvestment();
        long cumRevenue    = 0L;
        long cumCost       = 0L;
        int  bepMonth      = -1;
        long totalRevenue  = 0L;
        long totalFixed    = 0L;
        long minCashflow   = cashBalance;
        int  prevPatients  = 0;
        double totalGrowth = 0;

        for (int month = 1; month <= 36; month++) {

            // 환자수 S커브 성장
            double growth   = Math.min(1.0, 0.3 + month * 0.03);
            int    patients = (int)(basePatientsPerDay * WORKING_DAYS
                    * areaFactor * growth);

            // 매출 (건당 수가 × 환자수)
            double revenuePerPatient = getRevenuePerPatient(req.getDeptCategory());
            long   revenue           = Math.round(patients * revenuePerPatient);

            // 변동비
            long variableCost = Math.round(revenue * VARIABLE_COST_RATIO);

            // 영업이익
            long operatingProfit = revenue - variableCost - fixedMonthly;

            // 이자비용
            long interest = (req.getLoanMonths() != null
                    && month <= req.getLoanMonths())
                    ? Math.round(monthlyInterest) : 0L;

            long netProfit = operatingProfit - interest;

            // 현금흐름
            long operatingCF  = netProfit;
            long investingCF  = (month == 1) ? -req.getInitialInvestment() : 0L;
            long financingCF  = (month == 1) ?  req.getLoanAmount() : 0L;

            cashBalance += netProfit;
            cumRevenue  += revenue;
            cumCost     += (variableCost + fixedMonthly + interest);

            cumRevList.add(cumRevenue);
            cumCostList.add(cumCost);

            totalRevenue += revenue;
            totalFixed   += fixedMonthly;

            if (bepMonth == -1 && operatingProfit > 0) bepMonth = month;
            if (cashBalance < minCashflow) minCashflow = cashBalance;

            // 환자수 증가율 평균 계산용
            if (month > 1 && prevPatients > 0) {
                totalGrowth += (double)(patients - prevPatients) / prevPatients * 100;
            }
            prevPatients = patients;

            monthlyList.add(MonthlyData.builder()
                    .month(month)
                    .revenue(revenue)
                    .variableCost(variableCost)
                    .fixedCost(fixedMonthly)
                    .operatingProfit(operatingProfit)
                    .interestExpense(interest)
                    .netProfit(netProfit)
                    .operatingCashFlow(operatingCF)
                    .investingCashFlow(investingCF)
                    .financingCashFlow(financingCF)
                    .cumulativeCash(cashBalance)
                    .build());
        }

        double fixedCostRatio    = totalRevenue > 0
                ? (double) totalFixed / totalRevenue * 100 : 0;
        double avgPatientGrowth  = totalGrowth / 35;
        String cpaGrade          = gradeCpa(req.getMonthlyMarketing(),
                basePatientsPerDay * WORKING_DAYS);

        return SimulationResult.builder()
                .bepMonth(bepMonth == -1 ? 999 : bepMonth)
                .fixedCostRatio(Math.round(fixedCostRatio * 10) / 10.0)
                .finalCashBalance(cashBalance)
                .bepMonthBase(bepMonth == -1 ? 999 : bepMonth)
                .patientGrowthRate(Math.round(avgPatientGrowth * 10) / 10.0)
                .marketingCpa(cpaGrade)
                .cumulativeRevenue(cumRevList)
                .cumulativeCost(cumCostList)
                .monthly(monthlyList)
                .kpiMessages(generateKpiMessages(bepMonth, minCashflow,
                        req.getInitialInvestment()))
                .build();
    }

    // 진료과목별 하루 기준 환자수
    private int getBasePatients(String dept) {
        if (dept == null) return 40;
        return switch (dept) {
            case "내과"       -> 40;
            case "소아청소년과" -> 35;
            case "피부과"     -> 25;
            case "정형외과"   -> 22;
            case "이비인후과" -> 38;
            case "안과"       -> 20;
            default           -> 30;
        };
    }

    // 진료과목별 건당 수가 (만원)
    private double getRevenuePerPatient(String dept) {
        if (dept == null) return 5.0;
        return switch (dept) {
            case "내과"       -> 4.5;
            case "소아청소년과" -> 4.0;
            case "피부과"     -> 9.0;
            case "정형외과"   -> 8.5;
            case "이비인후과" -> 4.2;
            case "안과"       -> 10.0;
            default           -> 5.0;
        };
    }

    // 상권 보정 계수
    private double getAreaFactor(String region) {
        if (region == null) return 0.78;
        if (region.contains("강남") || region.contains("서초")
                || region.contains("송파")) return 1.0;
        if (region.contains("노원") || region.contains("도봉")
                || region.contains("중랑")) return 0.60;
        return 0.78;
    }

    // 월 고정비 합산
    private long calcFixedCost(SimulationRequest req) {
        // 인건비 직접 입력값 합산
        long laborCost = 0L;
        if (req.getStaffList() != null) {
            laborCost = req.getStaffList().stream()
                    .mapToLong(s -> s.getSalary() * s.getCount())
                    .sum();
        }
        long insurance   = Math.round(laborCost * 0.106); // 4대보험
        long rent        = req.getMonthlyRent() != null ? req.getMonthlyRent() : 0L;
        long marketing   = req.getMonthlyMarketing() != null ? req.getMonthlyMarketing() : 0L;
        long depreciation = req.getInitialInvestment() / 60; // 60개월 감가상각

        return laborCost + insurance + rent + marketing + depreciation;
    }

    // 월 이자 계산
    private double calcMonthlyInterest(SimulationRequest req) {
        if (req.getLoanAmount() == null || req.getLoanRate() == null) return 0;
        return (req.getLoanAmount() * (req.getLoanRate() / 100.0)) / 12.0;
    }

    // CPA 등급
    private String gradeCpa(Long marketing, int basePatients) {
        if (marketing == null || marketing == 0) return "미입력";
        double cpa = (double) marketing / (basePatients * 0.1);
        if (cpa < 10) return "우수";
        if (cpa < 20) return "보통";
        return "미흡";
    }

    // KPI 피드백 메시지
    private List<String> generateKpiMessages(int bepMonth, long minCashflow,
                                             long initialCapital) {
        List<String> messages = new ArrayList<>();

        if (minCashflow < 0) {
            messages.add("현금잔고가 마이너스로 떨어지는 구간이 있습니다. 초기 투자금 확대를 검토하세요.");
        } else if (minCashflow < initialCapital * 0.2) {
            messages.add("현금 여유가 초기 투자금의 20% 미만으로 줄어드는 시점이 있습니다.");
        }

        if (bepMonth == -1) {
            messages.add("36개월 내 손익분기점 달성이 어렵습니다. 비용 구조를 재검토하세요.");
        } else if (bepMonth <= 12) {
            messages.add("BEP " + bepMonth + "개월 달성 예상 — 매우 양호한 수준입니다.");
        } else {
            messages.add("BEP " + bepMonth + "개월 달성 예상 — 초기 마케팅 강화를 권장합니다.");
        }

        return messages;
    }
}

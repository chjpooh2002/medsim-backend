package com.medsim.backend.service;

import com.medsim.backend.domain.MonthlyData;
import com.medsim.backend.dto.request.SimulationRequest;
import com.medsim.backend.dto.response.KpiMessage;
import com.medsim.backend.dto.response.SimulationResult;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;

@Service
public class SimulationService {

    private static final int    WORKING_DAYS        = 22;    // 월 평균 진료일수
    private static final double VARIABLE_COST_RATIO = 0.15;  // 변동비율 (매출의 15%)
    private static final double OTHER_MGMT_RATIO    = 0.02;  // 기타관리비 (카드수수료, 소모품비 등, 매출의 2%)
    private static final double TAX_RATE            = 0.20;  // 실효세율 20% 고정
    private static final double INSURANCE_RATE      = 0.106; // 4대보험 요율
    private static final int    DEPRECIATION_MONTHS = 60;    // 감가상각 기간 (5년)

    public SimulationResult simulate(SimulationRequest req) {

        int    basePatientsPerDay = getBasePatients(req.getDeptCategory());
        double areaFactor         = getAreaFactor(req.getRegionSiGun());
        long   baseFixedMonthly   = calcBaseFixedCost(req); // 매출 무관 고정비

        // 대출 여부 확인 및 월 균등 원금 산출
        boolean hasLoan          = req.getLoanAmount()  != null
                && req.getLoanAmount()  >  0
                && req.getLoanMonths()  != null
                && req.getLoanRate()    != null;
        long    monthlyPrincipal = hasLoan
                ? req.getLoanAmount() / req.getLoanMonths() : 0L;

        List<MonthlyData> monthlyList = new ArrayList<>();
        List<Long>        cumRevList  = new ArrayList<>();
        List<Long>        cumCostList = new ArrayList<>();

        long cashBalance   = 0L;
        long cumRevenue    = 0L;
        long cumCost       = 0L;
        int  bepMonth      = -1;   // -1이면 36개월 내 미달성
        long totalRevenue  = 0L;
        long totalFixed    = 0L;
        int  firstPatients = -1;
        int  lastPatients  = 0;

        for (int month = 1; month <= 36; month++) {

            // ① 환자수 — S커브로 초반엔 천천히, 이후 안정적으로 증가
            double growth   = Math.min(1.0, 0.3 + month * 0.03);
            int    patients = (int)(basePatientsPerDay * WORKING_DAYS * areaFactor * growth);
            if (firstPatients == -1) firstPatients = patients;
            lastPatients = patients;

            // ② 매출액
            double revenuePerPatient = getRevenuePerPatient(req.getDeptCategory());
            long   revenue           = Math.round(patients * revenuePerPatient);

            // ③ 변동비 + 기타관리비 (둘 다 매출 연동)
            long variableCost  = Math.round(revenue * VARIABLE_COST_RATIO);
            long otherMgmtCost = Math.round(revenue * OTHER_MGMT_RATIO);
            long fixedMonthly  = baseFixedMonthly + otherMgmtCost;

            // ④ 영업이익
            long operatingProfit = revenue - variableCost - fixedMonthly;

            // ⑤ 이자비용 — 원금 균등 상환: 잔여 원금이 줄수록 이자도 감소
            long interest        = 0L;
            long principalPayment = 0L;
            if (hasLoan && month <= req.getLoanMonths()) {
                long remainingLoan = req.getLoanAmount() - (monthlyPrincipal * (month - 1));
                interest           = Math.round(remainingLoan * (req.getLoanRate() / 100.0) / 12.0);
                principalPayment   = monthlyPrincipal;
            }

            // ⑥ 세금 — 이익이 날 때만 부과 (적자일 때 0 처리, 환급 없음)
            long tax = operatingProfit > 0
                    ? Math.round(operatingProfit * TAX_RATE) : 0L;

            // ⑦ 당기순이익
            long netProfit = operatingProfit - interest - tax;

            // ⑧ 현금흐름표
            long operatingCF = revenue - variableCost - fixedMonthly - interest - tax;
            long investingCF = (month == 1) ? -req.getInitialInvestment() : 0L;
            long financingCF = (month == 1)
                    ? (req.getLoanAmount() != null ? req.getLoanAmount() - principalPayment : 0L)
                    : -principalPayment;

            cashBalance += operatingCF + investingCF + financingCF;
            cumRevenue  += revenue;
            cumCost     += (variableCost + fixedMonthly + interest + tax);

            cumRevList.add(cumRevenue);
            cumCostList.add(cumCost);
            totalRevenue += revenue;
            totalFixed   += fixedMonthly;

            if (bepMonth == -1 && netProfit > 0) bepMonth = month;

            monthlyList.add(MonthlyData.builder()
                    .month(month)
                    .revenue(revenue)
                    .variableCost(variableCost)
                    .fixedCost(fixedMonthly)
                    .operatingProfit(operatingProfit)
                    .interestExpense(interest)
                    .taxExpense(tax)
                    .netProfit(netProfit)
                    .operatingCashFlow(operatingCF)
                    .investingCashFlow(investingCF)
                    .financingCashFlow(financingCF)
                    .cumulativeCash(cashBalance)
                    .build());
        }

        // KPI 최종 산출
        double fixedCostRatio    = totalRevenue > 0
                ? (double) totalFixed / totalRevenue * 100 : 0;
        double patientGrowthRate = firstPatients > 0
                ? ((double)(lastPatients - firstPatients) / firstPatients) * 100 : 0;
        long   bepTargetRevenue  = calcBepTargetRevenue(baseFixedMonthly);
        long   cpaValue          = calcCpa(req);

        return SimulationResult.builder()
                .bepMonth(bepMonth == -1 ? 999 : bepMonth)
                .fixedCostRatio(Math.round(fixedCostRatio * 10) / 10.0)
                .finalCashBalance(cashBalance)
                .bepTargetRevenue(bepTargetRevenue)
                .patientGrowthRate(Math.round(patientGrowthRate * 10) / 10.0)
                .marketingCpa(cpaValue)
                .cumulativeRevenue(cumRevList)
                .cumulativeCost(cumCostList)
                .monthly(monthlyList)
                .kpiMessages(generateKpiMessages(bepMonth, cashBalance,
                        fixedCostRatio, patientGrowthRate, totalFixed / 36))
                .build();
    }

    // BEP 목표 매출 = 고정비 ÷ (1 - 변동비율 - 기타관리비율)
    private long calcBepTargetRevenue(long baseFixed) {
        return Math.round(baseFixed / (1.0 - VARIABLE_COST_RATIO - OTHER_MGMT_RATIO));
    }

    // CPA = 월 마케팅비 ÷ 신규 환자수 (신환 비율 30% 적용)
    private long calcCpa(SimulationRequest req) {
        if (req.getMonthlyMarketing() == null || req.getMonthlyMarketing() == 0) return 0L;
        int  basePatients = getBasePatients(req.getDeptCategory()) * WORKING_DAYS;
        long newPatients  = Math.round(basePatients * 0.3);
        return newPatients > 0 ? req.getMonthlyMarketing() / newPatients : 0L;
    }

    // 매출 무관 고정비 합산 (기타관리비는 매출 연동이라 루프 안에서 별도 계산)
    private long calcBaseFixedCost(SimulationRequest req) {
        long laborCost = 0L;
        if (req.getStaffList() != null) {
            laborCost = req.getStaffList().stream()
                    .mapToLong(s -> s.getSalary() * s.getCount()).sum();
        }
        long insurance    = Math.round(laborCost * INSURANCE_RATE);  // 4대보험
        long rent         = req.getMonthlyRent()      != null ? req.getMonthlyRent()      : 0L;
        long marketing    = req.getMonthlyMarketing() != null ? req.getMonthlyMarketing() : 0L;
        long depreciation = req.getInitialInvestment() / DEPRECIATION_MONTHS; // 월 감가상각비
        return laborCost + insurance + rent + marketing + depreciation;
    }

    // 12가지 KPI 조건별 피드백 생성
    private List<KpiMessage> generateKpiMessages(
            int bepMonth, long finalCash, double fixedRatio,
            double growthRate, long avgFixed) {
        List<KpiMessage> messages = new ArrayList<>();
        long threeMonthOpex = avgFixed * 3; // 3개월치 운영비 (현금 안정 기준선)

        // BEP 달성 시점
        if      (bepMonth >= 1 && bepMonth <= 6)
            messages.add(new KpiMessage("평균보다 빠른 흑자 전환 — 비용 구조 및 매출 전략 효율적", "success"));
        else if (bepMonth >= 7 && bepMonth <= 12)
            messages.add(new KpiMessage("업계 평균 수준의 손익분기 달성", "warning"));
        else
            messages.add(new KpiMessage("손익분기 지연 — 고정비 구조 또는 매출 전략 전면 재검토 필요", "danger"));

        // 고정비 비율
        if      (fixedRatio <= 40)
            messages.add(new KpiMessage("고정비 비율 우수 — 수익 구조 안정적", "success"));
        else if (fixedRatio <= 59)
            messages.add(new KpiMessage("고정비 구조 적정 수준", "warning"));
        else
            messages.add(new KpiMessage("고정비 과부하 — 임대료/인건비 구조 재검토 필요", "danger"));

        // M36 현금잔고 (3개월치 운영비 이상이면 안정)
        if      (finalCash > threeMonthOpex)
            messages.add(new KpiMessage("현금 안정적 — 충분한 운전자본 확보", "success"));
        else if (finalCash > 0)
            messages.add(new KpiMessage("운전자본 부족 — 예상치 못한 지출 발생 시 위기 가능", "warning"));
        else
            messages.add(new KpiMessage("현금 고갈 위험 — 즉시 비용 절감 또는 운전자본 확보 필요", "danger"));

        // 환자수 증가율
        if      (growthRate >= 5)
            messages.add(new KpiMessage("환자수 성장세 우수 — 마케팅 및 리텐션 전략 성공적", "success"));
        else if (growthRate >= 0)
            messages.add(new KpiMessage("환자수 성장 정체 — 마케팅 채널 재점검 필요", "warning"));
        else
            messages.add(new KpiMessage("환자수 이탈 심화 — 원인 분석 및 긴급 대응 필요", "danger"));

        return messages;
    }

    // 진료과목별 하루 기준 환자수 (하드코딩, DB 구축 후 교체 예정)
    private int getBasePatients(String dept) {
        if (dept == null) return 40;
        return switch (dept) {
            case "내과"        -> 40;
            case "소아청소년과" -> 35;
            case "피부과"      -> 25;
            case "정형외과"    -> 22;
            case "이비인후과"  -> 38;
            case "안과"        -> 20;
            default            -> 30;
        };
    }

    // 진료과목별 건당 수가 (만원, 하드코딩, DB 구축 후 교체 예정)
    private double getRevenuePerPatient(String dept) {
        if (dept == null) return 5.0;
        return switch (dept) {
            case "내과"        -> 4.5;
            case "소아청소년과" -> 4.0;
            case "피부과"      -> 9.0;
            case "정형외과"    -> 8.5;
            case "이비인후과"  -> 4.2;
            case "안과"        -> 10.0;
            default            -> 5.0;
        };
    }

    // 상권 보정 계수 — 지역별 유동인구·경쟁 강도를 반영한 매출 보정값
    // (하드코딩, 향후 상권분석 엔진으로 교체 예정)
    private double getAreaFactor(String region) {
        if (region == null) return 0.78;
        if (region.contains("강남") || region.contains("서초")
                || region.contains("송파")) return 1.0;
        if (region.contains("노원") || region.contains("도봉")
                || region.contains("중랑")) return 0.60;
        return 0.78;
    }
}

package com.medsim.backend.service;

import com.medsim.backend.domain.MonthlyData;
import com.medsim.backend.domain.SimulationConfig;
import com.medsim.backend.domain.SimulationEvent;
import com.medsim.backend.dto.request.SimulationRequest;
import com.medsim.backend.dto.request.StaffRequest;
import com.medsim.backend.dto.response.KpiMessage;
import com.medsim.backend.dto.response.SimulationResult;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class SimulationService {

    private static final int    WORKING_DAYS        = 22;
    private static final double VARIABLE_COST_RATIO = 0.15;
    private static final double OTHER_MGMT_RATIO    = 0.02;
    private static final double TAX_RATE            = 0.20;
    private static final double INSURANCE_RATE      = 0.106;
    private static final int    DEPRECIATION_MONTHS = 60;

    public SimulationResult simulate(SimulationRequest req) {
        return runSimulation(req, Collections.emptyList());
    }

    public SimulationResult getMockResult() {
        return runSimulation(buildMockRequest(), buildMockEvents());
    }

    private SimulationResult runSimulation(SimulationRequest req, List<SimulationEvent> events) {

        int    basePatientsPerDay = getBasePatients(req.getDeptCategory());
        double areaFactor         = getAreaFactor(req.getRegionSiGun());
        double revenuePerPatient  = getRevenuePerPatient(req.getDeptCategory());

        // 고정비 컴포넌트 분리 (매출 무관, 36개월 고정)
        long laborCost = 0L;
        if (req.getStaffList() != null) {
            laborCost = req.getStaffList().stream()
                    .mapToLong(s -> s.getSalary() * s.getCount()).sum();
        }
        long insuranceCost    = Math.round(laborCost * INSURANCE_RATE);
        long rentCost         = req.getMonthlyRent()      != null ? req.getMonthlyRent()      : 0L;
        long marketingCost    = req.getMonthlyMarketing() != null ? req.getMonthlyMarketing() : 0L;
        long depreciationCost = req.getInitialInvestment() / DEPRECIATION_MONTHS;
        long baseFixedMonthly = laborCost + insuranceCost + rentCost + marketingCost + depreciationCost;

        // 대출 계산
        boolean hasLoan = req.getLoanAmount() != null && req.getLoanAmount() > 0
                       && req.getLoanMonths() != null && req.getLoanRate()   != null;
        long monthlyPrincipal = hasLoan ? req.getLoanAmount() / req.getLoanMonths() : 0L;

        // 루프 집계 변수
        List<MonthlyData> monthlyList = new ArrayList<>();
        List<Long>        cumRevList  = new ArrayList<>();
        List<Long>        cumCostList = new ArrayList<>();

        long cashBalance   = req.getInitialInvestment();
        long cumRevenue    = 0L;
        long cumCost       = 0L;
        int  bepMonth      = -1;
        long totalRevenue  = 0L;
        long totalFixed    = 0L;
        int  firstPatients = -1;
        int  lastPatients  = 0;

        // 비용 항목별 36개월 누적 (costBreakdown용)
        long totalLaborCost    = 0L;
        long totalInsurance    = 0L;
        long totalRent         = 0L;
        long totalMarketing    = 0L;
        long totalDepreciation = 0L;
        long totalOtherMgmt    = 0L;
        long totalVariableCost = 0L;
        long totalInterest     = 0L;

        // 비재무 지표 초기값
        double reputation   = SimulationConfig.INITIAL_REPUTATION;
        double satisfaction = 3.0;
        double returnRate   = SimulationConfig.INITIAL_RETURN_RATE;
        double staffMorale  = 0.8;
        boolean isBankrupt  = false;

        for (int month = 1; month <= 36; month++) {

            // ① 이벤트 처리
            List<String> monthEvents     = new ArrayList<>();
            double       revMultiplier   = 1.0;
            long         extraCost       = 0L;
            for (SimulationEvent ev : events) {
                if (ev.getTriggerMonth() == month) {
                    monthEvents.add(ev.getEventId());
                    Map<String, Double> imp = ev.getImpactMap();
                    if (imp.containsKey("reputation"))   reputation   = clamp(reputation   + imp.get("reputation"),   0.0, 5.0);
                    if (imp.containsKey("satisfaction")) satisfaction = clamp(satisfaction + imp.get("satisfaction"), 0.0, 5.0);
                    if (imp.containsKey("returnRate"))   returnRate   = clamp(returnRate   + imp.get("returnRate"),   0.0, 1.0);
                    if (imp.containsKey("staffMorale"))  staffMorale  = clamp(staffMorale  + imp.get("staffMorale"),  0.0, 1.0);
                    if (imp.containsKey("revenue"))      revMultiplier += imp.get("revenue");
                    if (imp.containsKey("extraCost"))    extraCost    += Math.round(imp.get("extraCost"));
                }
            }

            // ② 환자수 (S커브)
            double growth   = Math.min(1.0, 0.3 + month * 0.03);
            int    patients = (int)(basePatientsPerDay * WORKING_DAYS * areaFactor * growth);
            if (firstPatients == -1) firstPatients = patients;
            lastPatients = patients;

            // ③ 매출액
            long revenue = Math.round(patients * revenuePerPatient * revMultiplier);

            // ④ 변동비 + 기타관리비 (매출 연동)
            long variableCost  = Math.round(revenue * VARIABLE_COST_RATIO);
            long otherMgmtCost = Math.round(revenue * OTHER_MGMT_RATIO);
            long fixedMonthly  = baseFixedMonthly + otherMgmtCost + extraCost;

            // ⑤ 영업이익
            long operatingProfit = revenue - variableCost - fixedMonthly;

            // ⑥ 이자비용 (원금 균등 상환)
            long interest         = 0L;
            long principalPayment = 0L;
            if (hasLoan && month <= req.getLoanMonths()) {
                long remainingLoan = req.getLoanAmount() - (monthlyPrincipal * (month - 1));
                interest           = Math.round(remainingLoan * (req.getLoanRate() / 100.0) / 12.0);
                principalPayment   = monthlyPrincipal;
            }

            // ⑦ 세금 (적자 시 0, 환급 없음)
            long tax = operatingProfit > 0 ? Math.round(operatingProfit * TAX_RATE) : 0L;

            // ⑧ 당기순이익
            long netProfit = operatingProfit - interest - tax;

            // ⑨ 현금흐름표
            long operatingCF = revenue - variableCost - fixedMonthly - interest - tax;
            long investingCF = (month == 1) ? -req.getInitialInvestment() : 0L;
            long financingCF = (month == 1)
                    ? (req.getLoanAmount() != null ? req.getLoanAmount() - principalPayment : 0L)
                    : -principalPayment;

            cashBalance += operatingCF + investingCF + financingCF;
            if (cashBalance <= 0 && !isBankrupt) isBankrupt = true;

            cumRevenue += revenue;
            cumCost    += (variableCost + fixedMonthly + interest + tax);
            cumRevList.add(cumRevenue);
            cumCostList.add(cumCost);
            totalRevenue += revenue;
            totalFixed   += fixedMonthly;

            // 비용 항목 누적
            totalLaborCost    += laborCost;
            totalInsurance    += insuranceCost;
            totalRent         += rentCost;
            totalMarketing    += marketingCost;
            totalDepreciation += depreciationCost;
            totalOtherMgmt    += otherMgmtCost;
            totalVariableCost += variableCost;
            totalInterest     += interest;

            if (bepMonth == -1 && netProfit > 0) bepMonth = month;

            // ⑩ 비재무 지표 갱신
            // 직원 사기: 흑자 시 소폭 상승, 적자 지속 시 하락
            double moraleDelta = (operatingProfit > 0) ? 0.005 : -0.02;
            staffMorale = clamp(staffMorale + moraleDelta, 0.0, 1.0);

            // 환자 만족도: 직원 사기 높을수록, 재무 여건 좋을수록 상승
            double satDelta = (staffMorale - 0.6) * 0.05;
            if (operatingProfit < 0) satDelta -= 0.03;
            satisfaction = clamp(satisfaction + satDelta, 0.0, 5.0);

            // 병원 평판: 만족도 방향으로 수렴 (입소문 지연 반영)
            reputation = clamp(reputation + (satisfaction - reputation) * 0.08, 0.0, 5.0);

            // 재진율: 평판 기반 목표값으로 수렴 (평판 3.0 → 목표 0.30)
            double rrTarget = reputation / 10.0;
            returnRate = clamp(returnRate + (rrTarget - returnRate) * 0.05, 0.0, 1.0);

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
                    .reputationScore(round2(reputation))
                    .patientSatisfaction(round2(satisfaction))
                    .returnPatientRate(round3(returnRate))
                    .staffMorale(round3(staffMorale))
                    .activeEvents(monthEvents)
                    .build());
        }

        // KPI 최종 산출
        double fixedCostRatio    = totalRevenue > 0
                ? (double) totalFixed / totalRevenue * 100 : 0;
        double patientGrowthRate = firstPatients > 0
                ? ((double)(lastPatients - firstPatients) / firstPatients) * 100 : 0;
        long bepTargetRevenue    = calcBepTargetRevenue(baseFixedMonthly);
        long cpaValue            = calcCpa(req);

        // 비용 항목별 합계 맵
        Map<String, Long> costBreakdown = new LinkedHashMap<>();
        costBreakdown.put("인건비",    totalLaborCost);
        costBreakdown.put("4대보험",   totalInsurance);
        costBreakdown.put("임대료",    totalRent);
        costBreakdown.put("마케팅비",  totalMarketing);
        costBreakdown.put("감가상각비", totalDepreciation);
        costBreakdown.put("기타관리비", totalOtherMgmt);
        costBreakdown.put("변동비",    totalVariableCost);
        costBreakdown.put("이자비용",  totalInterest);

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
                .finalReputation(round2(reputation))
                .finalSatisfaction(round2(satisfaction))
                .finalReturnRate(round3(returnRate))
                .costBreakdown(costBreakdown)
                .isBankrupt(isBankrupt)
                .grade(calcGrade(isBankrupt, bepMonth, reputation, cashBalance))
                .build();
    }

    // ── 등급 산출 ──────────────────────────────────────────────────────────────
    private String calcGrade(boolean isBankrupt, int bepMonth, double finalRep, long finalCash) {
        if (isBankrupt || bepMonth == -1)         return "F";
        if (bepMonth <= 6  && finalRep >= 4.0 && finalCash > 0) return "S";
        if (bepMonth <= 12 && finalRep >= 3.5)    return "A";
        if (bepMonth <= 18)                        return "B";
        return "C";
    }

    // ── 목업 데이터 빌더 ────────────────────────────────────────────────────────
    private SimulationRequest buildMockRequest() {
        SimulationRequest req = new SimulationRequest();
        req.setDeptCategory("내과");
        req.setRegionSiGun("강남구");
        req.setInitialInvestment(30000L);  // 3억
        req.setLoanAmount(15000L);         // 1.5억
        req.setLoanRate(4.5);
        req.setLoanMonths(36);
        req.setMonthlyRent(500L);
        req.setMonthlyMarketing(200L);

        StaffRequest doctor = new StaffRequest();
        doctor.setRole("의사");  doctor.setSalary(1000L); doctor.setCount(1);

        StaffRequest nurse = new StaffRequest();
        nurse.setRole("간호사"); nurse.setSalary(300L);  nurse.setCount(2);

        StaffRequest admin = new StaffRequest();
        admin.setRole("원무행정"); admin.setSalary(250L); admin.setCount(1);

        req.setStaffList(Arrays.asList(doctor, nurse, admin));
        return req;
    }

    private List<SimulationEvent> buildMockEvents() {
        return Arrays.asList(
            SimulationEvent.builder()
                .eventId("EVT_COMPETITOR_OPEN").triggerMonth(6)
                .eventType(SimulationEvent.EventType.COMPETITOR_OPEN)
                .description("인근에 경쟁 내과 개원 — 신환 유입 감소")
                .impactMap(Map.of("revenue", -0.08, "reputation", -0.2, "returnRate", -0.02))
                .build(),
            SimulationEvent.builder()
                .eventId("EVT_REVIEW_VIRAL").triggerMonth(12)
                .eventType(SimulationEvent.EventType.REVIEW_VIRAL)
                .description("포털 긍정 리뷰 바이럴 — 신환 급증")
                .impactMap(Map.of("revenue", 0.15, "reputation", 0.5, "satisfaction", 0.3))
                .build(),
            SimulationEvent.builder()
                .eventId("EVT_EQUIPMENT_BREAK").triggerMonth(20)
                .eventType(SimulationEvent.EventType.EQUIPMENT_BREAK)
                .description("초음파 장비 고장 — 수리비 발생 및 진료 차질")
                .impactMap(Map.of("revenue", -0.05, "extraCost", 300.0, "satisfaction", -0.2))
                .build(),
            SimulationEvent.builder()
                .eventId("EVT_PATIENT_SURGE").triggerMonth(28)
                .eventType(SimulationEvent.EventType.PATIENT_SURGE)
                .description("독감 시즌 환자 급증")
                .impactMap(Map.of("revenue", 0.20, "staffMorale", -0.1, "satisfaction", -0.1))
                .build()
        );
    }

    // ── 기존 헬퍼 메서드 ────────────────────────────────────────────────────────
    private long calcBepTargetRevenue(long baseFixed) {
        return Math.round(baseFixed / (1.0 - VARIABLE_COST_RATIO - OTHER_MGMT_RATIO));
    }

    private long calcCpa(SimulationRequest req) {
        if (req.getMonthlyMarketing() == null || req.getMonthlyMarketing() == 0) return 0L;
        int  basePatients = getBasePatients(req.getDeptCategory()) * WORKING_DAYS;
        long newPatients  = Math.round(basePatients * 0.3);
        return newPatients > 0 ? Math.round((double) req.getMonthlyMarketing() / newPatients) : 0L;
    }

    private List<KpiMessage> generateKpiMessages(
            int bepMonth, long finalCash, double fixedRatio,
            double growthRate, long avgFixed) {
        List<KpiMessage> messages = new ArrayList<>();
        long threeMonthOpex = avgFixed * 3;

        if      (bepMonth >= 1 && bepMonth <= 6)
            messages.add(new KpiMessage("평균보다 빠른 흑자 전환 — 비용 구조 및 매출 전략 효율적", "success"));
        else if (bepMonth >= 7 && bepMonth <= 12)
            messages.add(new KpiMessage("업계 평균 수준의 손익분기 달성", "warning"));
        else
            messages.add(new KpiMessage("손익분기 지연 — 고정비 구조 또는 매출 전략 전면 재검토 필요", "danger"));

        if      (fixedRatio <= 40)
            messages.add(new KpiMessage("고정비 비율 우수 — 수익 구조 안정적", "success"));
        else if (fixedRatio <= 59)
            messages.add(new KpiMessage("고정비 구조 적정 수준", "warning"));
        else
            messages.add(new KpiMessage("고정비 과부하 — 임대료/인건비 구조 재검토 필요", "danger"));

        if      (finalCash > threeMonthOpex)
            messages.add(new KpiMessage("현금 안정적 — 충분한 운전자본 확보", "success"));
        else if (finalCash > 0)
            messages.add(new KpiMessage("운전자본 부족 — 예상치 못한 지출 발생 시 위기 가능", "warning"));
        else
            messages.add(new KpiMessage("현금 고갈 위험 — 즉시 비용 절감 또는 운전자본 확보 필요", "danger"));

        if      (growthRate >= 5)
            messages.add(new KpiMessage("환자수 성장세 우수 — 마케팅 및 리텐션 전략 성공적", "success"));
        else if (growthRate >= 0)
            messages.add(new KpiMessage("환자수 성장 정체 — 마케팅 채널 재점검 필요", "warning"));
        else
            messages.add(new KpiMessage("환자수 이탈 심화 — 원인 분석 및 긴급 대응 필요", "danger"));

        return messages;
    }

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

    private double getAreaFactor(String region) {
        if (region == null) return 0.78;
        if (region.contains("강남") || region.contains("서초")
                || region.contains("송파")) return 1.0;
        if (region.contains("노원") || region.contains("도봉")
                || region.contains("중랑")) return 0.60;
        return 0.78;
    }

    private double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private double round2(double v) { return Math.round(v * 100) / 100.0; }
    private double round3(double v) { return Math.round(v * 1000) / 1000.0; }
}

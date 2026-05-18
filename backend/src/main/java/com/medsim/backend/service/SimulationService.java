package com.medsim.backend.service;

import com.medsim.backend.domain.*;
import com.medsim.backend.dto.request.SimulationRequest;
import com.medsim.backend.dto.request.StaffRequest;
import com.medsim.backend.dto.response.KpiMessage;
import com.medsim.backend.dto.response.SimulationResult;
import com.medsim.backend.dto.response.SimulationTurnResult;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class SimulationService {

    // ── 재무 상수 (기존 유지) ─────────────────────────────────────────────────────
    private static final int    WORKING_DAYS        = 22;
    private static final double VARIABLE_COST_RATIO = 0.15;
    private static final double OTHER_MGMT_RATIO    = 0.02;
    private static final double TAX_RATE            = 0.20;
    private static final double INSURANCE_RATE      = 0.106;
    private static final int    DEPRECIATION_MONTHS = 60;

    // ── 턴제 시뮬레이션 인메모리 저장소 ──────────────────────────────────────────
    private final Map<String, SimulationState> simulationStore = new ConcurrentHashMap<>();

    // ═══════════════════════════════════════════════════════════════════════════
    // Public API — 일괄 계산 (기존)
    // ═══════════════════════════════════════════════════════════════════════════

    public SimulationResult simulate(SimulationRequest req) {
        return runSimulation(req, Collections.emptyList());
    }

    public SimulationResult getMockResult() {
        return runSimulation(buildMockRequest(), buildMockEvents());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Public API — 턴제 (신규)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * 시뮬레이션 시작 — 1개월차를 실행하고 상태를 반환한다.
     * 반환된 simulationId 로 이후 nextTurn 을 호출한다.
     */
    public SimulationTurnResult startSimulation(SimulationRequest req) {
        String simId = UUID.randomUUID().toString();

        SimulationState state = SimulationState.builder()
                .simulationId(simId)
                .currentMonth(1)
                .cashBalance(req.getInitialInvestment())
                .patientsPerDay(0)
                .reputationScore(SimulationConfig.INITIAL_REPUTATION)
                .satisfactionScore(3.0)
                .returnPatientRate(SimulationConfig.INITIAL_RETURN_RATE)
                .staffMorale(0.8)
                .monthlyHistory(new ArrayList<>())
                .bankrupt(false)
                .completed(false)
                .initialSetup(req)
                .pendingEvents(new ArrayList<>(buildMockEvents()))
                .build();

        MonthlyData monthData = runOneTurn(state, Collections.emptyList());
        advanceState(state, 1);
        simulationStore.put(simId, state);

        return buildTurnResult(simId, 1, monthData, state);
    }

    /**
     * 의사결정을 제출하고 다음 달을 실행한다.
     *
     * @param simulationId startSimulation() 이 반환한 ID
     * @param decisionIds  이번 달 적용할 Decision ID 목록 (빈 리스트 가능)
     */
    public SimulationTurnResult nextTurn(String simulationId, List<String> decisionIds) {
        SimulationState state = simulationStore.get(simulationId);
        if (state == null) {
            throw new IllegalArgumentException("존재하지 않는 시뮬레이션입니다: " + simulationId);
        }
        if (state.isCompleted() || state.isBankrupt()) {
            throw new IllegalStateException("이미 종료된 시뮬레이션입니다.");
        }

        List<Decision> selected = getAvailableDecisions().stream()
                .filter(d -> decisionIds.contains(d.getDecisionId()))
                .collect(Collectors.toList());

        int thisTurnMonth = state.getCurrentMonth();
        MonthlyData monthData = runOneTurn(state, selected);
        advanceState(state, thisTurnMonth);

        return buildTurnResult(simulationId, thisTurnMonth, monthData, state);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 턴제 핵심 로직
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * 단일 월 계산 — 기존 runSimulation 루프 바디와 동일한 재무 공식 사용.
     * 결과를 state.monthlyHistory 에 추가하고 MonthlyData 를 반환한다.
     */
    private MonthlyData runOneTurn(SimulationState state, List<Decision> decisions) {
        SimulationRequest req = state.getInitialSetup();
        int month = state.getCurrentMonth();

        // 재무 상수 재계산 (request 기반)
        int    basePatientsPerDay = getBasePatients(req.getDeptCategory());
        double areaFactor         = getAreaFactor(req.getRegionSiGun());
        double revenuePerPatient  = getRevenuePerPatient(req.getDeptCategory());

        long laborCost        = computeLaborCost(req);
        long insuranceCost    = Math.round(laborCost * INSURANCE_RATE);
        long rentCost         = req.getMonthlyRent()      != null ? req.getMonthlyRent()      : 0L;
        long marketingCost    = req.getMonthlyMarketing() != null ? req.getMonthlyMarketing() : 0L;
        long depreciationCost = req.getInitialInvestment() / DEPRECIATION_MONTHS;
        long baseFixedMonthly = laborCost + insuranceCost + rentCost + marketingCost + depreciationCost;

        boolean hasLoan = req.getLoanAmount() != null && req.getLoanAmount() > 0
                       && req.getLoanMonths() != null && req.getLoanRate()   != null;
        long monthlyPrincipal = hasLoan ? req.getLoanAmount() / req.getLoanMonths() : 0L;

        // ① 의사결정 효과 적용
        double revMultiplier = 1.0;
        long   extraCost     = 0L;
        for (Decision d : decisions) {
            Map<String, Double> eff = d.getEffectMap();
            if (eff.containsKey("revenue"))      revMultiplier += eff.get("revenue");
            if (eff.containsKey("fixedCost"))    extraCost     += Math.round(eff.get("fixedCost"));
            if (eff.containsKey("reputation"))   state.setReputationScore(clamp(state.getReputationScore()   + eff.get("reputation"),   0, 5));
            if (eff.containsKey("satisfaction")) state.setSatisfactionScore(clamp(state.getSatisfactionScore() + eff.get("satisfaction"), 0, 5));
            if (eff.containsKey("returnRate"))   state.setReturnPatientRate(clamp(state.getReturnPatientRate() + eff.get("returnRate"),   0, 1));
            if (eff.containsKey("staffMorale"))  state.setStaffMorale(clamp(state.getStaffMorale()           + eff.get("staffMorale"),   0, 1));
        }

        // ② 이벤트 효과 적용
        List<String> activeEventIds = new ArrayList<>();
        for (SimulationEvent ev : state.getPendingEvents()) {
            if (ev.getTriggerMonth() == month) {
                activeEventIds.add(ev.getEventId());
                Map<String, Double> imp = ev.getImpactMap();
                if (imp.containsKey("revenue"))      revMultiplier += imp.get("revenue");
                if (imp.containsKey("extraCost"))    extraCost     += Math.round(imp.get("extraCost"));
                if (imp.containsKey("reputation"))   state.setReputationScore(clamp(state.getReputationScore()   + imp.get("reputation"),   0, 5));
                if (imp.containsKey("satisfaction")) state.setSatisfactionScore(clamp(state.getSatisfactionScore() + imp.get("satisfaction"), 0, 5));
                if (imp.containsKey("returnRate"))   state.setReturnPatientRate(clamp(state.getReturnPatientRate() + imp.get("returnRate"),   0, 1));
                if (imp.containsKey("staffMorale"))  state.setStaffMorale(clamp(state.getStaffMorale()           + imp.get("staffMorale"),   0, 1));
            }
        }

        // ③ 환자수 (S커브)
        double growth   = Math.min(1.0, 0.3 + month * 0.03);
        int    patients = (int)(basePatientsPerDay * WORKING_DAYS * areaFactor * growth);
        state.setPatientsPerDay((int)(basePatientsPerDay * areaFactor * growth));

        // ④ 매출액
        long revenue = Math.round(patients * revenuePerPatient * revMultiplier);

        // ⑤ 변동비 + 기타관리비
        long variableCost  = Math.round(revenue * VARIABLE_COST_RATIO);
        long otherMgmtCost = Math.round(revenue * OTHER_MGMT_RATIO);
        long fixedMonthly  = baseFixedMonthly + otherMgmtCost + extraCost;

        // ⑥ 영업이익
        long operatingProfit = revenue - variableCost - fixedMonthly;

        // ⑦ 이자비용 (원금 균등 상환)
        long interest         = 0L;
        long principalPayment = 0L;
        if (hasLoan && month <= req.getLoanMonths()) {
            long remainingLoan = req.getLoanAmount() - (monthlyPrincipal * (month - 1));
            interest           = Math.round(remainingLoan * (req.getLoanRate() / 100.0) / 12.0);
            principalPayment   = monthlyPrincipal;
        }

        // ⑧ 세금 / 순이익
        long tax       = operatingProfit > 0 ? Math.round(operatingProfit * TAX_RATE) : 0L;
        long netProfit = operatingProfit - interest - tax;

        // ⑨ 현금흐름
        long operatingCF = revenue - variableCost - fixedMonthly - interest - tax;
        long investingCF = (month == 1) ? -req.getInitialInvestment() : 0L;
        long financingCF = (month == 1)
                ? (req.getLoanAmount() != null ? req.getLoanAmount() - principalPayment : 0L)
                : -principalPayment;

        state.setCashBalance(state.getCashBalance() + operatingCF + investingCF + financingCF);
        if (state.getCashBalance() <= 0) state.setBankrupt(true);

        // ⑩ 비재무 지표 갱신 (기존 공식 유지)
        double moraleDelta = (operatingProfit > 0) ? 0.005 : -0.02;
        state.setStaffMorale(clamp(state.getStaffMorale() + moraleDelta, 0, 1));

        double satDelta = (state.getStaffMorale() - 0.6) * 0.05;
        if (operatingProfit < 0) satDelta -= 0.03;
        state.setSatisfactionScore(clamp(state.getSatisfactionScore() + satDelta, 0, 5));

        state.setReputationScore(clamp(
                state.getReputationScore() + (state.getSatisfactionScore() - state.getReputationScore()) * 0.08, 0, 5));

        double rrTarget = state.getReputationScore() / 10.0;
        state.setReturnPatientRate(clamp(
                state.getReturnPatientRate() + (rrTarget - state.getReturnPatientRate()) * 0.05, 0, 1));

        MonthlyData data = MonthlyData.builder()
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
                .cumulativeCash(state.getCashBalance())
                .reputationScore(round2(state.getReputationScore()))
                .patientSatisfaction(round2(state.getSatisfactionScore()))
                .returnPatientRate(round3(state.getReturnPatientRate()))
                .staffMorale(round3(state.getStaffMorale()))
                .activeEvents(activeEventIds)
                .build();

        state.getMonthlyHistory().add(data);
        return data;
    }

    /** 월 실행 후 currentMonth 갱신 및 완료 판정 */
    private void advanceState(SimulationState state, int executedMonth) {
        if (executedMonth == 36) {
            state.setCompleted(true);
        } else if (!state.isBankrupt()) {
            state.setCurrentMonth(executedMonth + 1);
        }
    }

    /** 턴 결과 DTO 조립 */
    private SimulationTurnResult buildTurnResult(
            String simId, int executedMonth, MonthlyData monthData, SimulationState state) {

        boolean done = state.isCompleted() || state.isBankrupt();

        List<SimulationEvent> nextEvents = done
                ? Collections.emptyList()
                : getEventsForMonth(state.getPendingEvents(), state.getCurrentMonth());

        return SimulationTurnResult.builder()
                .currentMonth(executedMonth)
                .monthlyData(monthData)
                .nextEvents(nextEvents)
                .availableDecisions(done ? Collections.emptyList() : getAvailableDecisions())
                .isBankrupt(state.isBankrupt())
                .isCompleted(state.isCompleted())
                .finalResult(done ? buildFinalResult(state) : null)
                .build();
    }

    /** 완료/파산 시 전체 SimulationResult 산출 */
    private SimulationResult buildFinalResult(SimulationState state) {
        SimulationRequest req     = state.getInitialSetup();
        List<MonthlyData> history = state.getMonthlyHistory();
        int monthsPlayed = history.size();

        long laborCost        = computeLaborCost(req);
        long insuranceCost    = Math.round(laborCost * INSURANCE_RATE);
        long rentCost         = req.getMonthlyRent()      != null ? req.getMonthlyRent()      : 0L;
        long marketingCost    = req.getMonthlyMarketing() != null ? req.getMonthlyMarketing() : 0L;
        long depreciationCost = req.getInitialInvestment() / DEPRECIATION_MONTHS;
        long baseFixedMonthly = laborCost + insuranceCost + rentCost + marketingCost + depreciationCost;

        // 누적 집계
        long totalRevenue = 0L, totalFixed = 0L;
        long cumRev = 0L, cumCost = 0L;
        int  bepMonth = -1;
        List<Long> cumRevList  = new ArrayList<>();
        List<Long> cumCostList = new ArrayList<>();

        for (MonthlyData md : history) {
            totalRevenue += md.getRevenue();
            totalFixed   += md.getFixedCost();
            cumRev  += md.getRevenue();
            cumCost += md.getVariableCost() + md.getFixedCost() + md.getInterestExpense() + md.getTaxExpense();
            cumRevList.add(cumRev);
            cumCostList.add(cumCost);
            if (bepMonth == -1 && md.getNetProfit() > 0) bepMonth = md.getMonth();
        }

        // 비용 항목별 합계
        long totalOtherMgmt    = history.stream().mapToLong(m -> Math.round(m.getRevenue() * OTHER_MGMT_RATIO)).sum();
        long totalVariableCost = history.stream().mapToLong(MonthlyData::getVariableCost).sum();
        long totalInterest     = history.stream().mapToLong(MonthlyData::getInterestExpense).sum();

        Map<String, Long> costBreakdown = new LinkedHashMap<>();
        costBreakdown.put("인건비",    laborCost     * monthsPlayed);
        costBreakdown.put("4대보험",   insuranceCost * monthsPlayed);
        costBreakdown.put("임대료",    rentCost      * monthsPlayed);
        costBreakdown.put("마케팅비",  marketingCost * monthsPlayed);
        costBreakdown.put("감가상각비", depreciationCost * monthsPlayed);
        costBreakdown.put("기타관리비", totalOtherMgmt);
        costBreakdown.put("변동비",    totalVariableCost);
        costBreakdown.put("이자비용",  totalInterest);

        // 환자 성장률 (S커브 공식으로 역산)
        double revenuePerPatient = getRevenuePerPatient(req.getDeptCategory());
        int firstPatients = history.isEmpty() ? 1
                : (int)(history.get(0).getRevenue() / revenuePerPatient);
        int lastPatients  = history.isEmpty() ? 1
                : (int)(history.get(history.size() - 1).getRevenue() / revenuePerPatient);
        double patientGrowthRate = firstPatients > 0
                ? ((double)(lastPatients - firstPatients) / firstPatients) * 100 : 0;

        long   finalCash      = state.getCashBalance();
        double fixedCostRatio = totalRevenue > 0 ? (double) totalFixed / totalRevenue * 100 : 0;
        long   avgFixed       = monthsPlayed > 0 ? totalFixed / monthsPlayed : 0L;

        return SimulationResult.builder()
                .bepMonth(bepMonth == -1 ? 999 : bepMonth)
                .fixedCostRatio(Math.round(fixedCostRatio * 10) / 10.0)
                .finalCashBalance(finalCash)
                .bepTargetRevenue(calcBepTargetRevenue(baseFixedMonthly))
                .patientGrowthRate(Math.round(patientGrowthRate * 10) / 10.0)
                .marketingCpa(calcCpa(req))
                .cumulativeRevenue(cumRevList)
                .cumulativeCost(cumCostList)
                .monthly(history)
                .kpiMessages(generateKpiMessages(bepMonth, finalCash, fixedCostRatio, patientGrowthRate, avgFixed))
                .finalReputation(round2(state.getReputationScore()))
                .finalSatisfaction(round2(state.getSatisfactionScore()))
                .finalReturnRate(round3(state.getReturnPatientRate()))
                .costBreakdown(costBreakdown)
                .isBankrupt(state.isBankrupt())
                .grade(calcGrade(state.isBankrupt(), bepMonth, state.getReputationScore(), finalCash))
                .build();
    }

    /** 선택 가능한 의사결정 옵션 (고정 4가지) */
    private List<Decision> getAvailableDecisions() {
        return Arrays.asList(
            Decision.builder()
                .decisionId("DEC_MARKETING_UP")
                .decisionType(Decision.DecisionType.MARKETING_UP)
                .description("마케팅 강화 — 당월 광고비 100만원 추가, 평판 +0.2")
                .effectMap(Map.of("fixedCost", 100.0, "reputation", 0.2))
                .build(),
            Decision.builder()
                .decisionId("DEC_STAFF_HIRE")
                .decisionType(Decision.DecisionType.STAFF_HIRE)
                .description("직원 채용 — 고정비 300만원 증가, 사기 +0.1, 만족도 +0.1")
                .effectMap(Map.of("fixedCost", 300.0, "staffMorale", 0.1, "satisfaction", 0.1))
                .build(),
            Decision.builder()
                .decisionId("DEC_COST_CUT")
                .decisionType(Decision.DecisionType.COST_CUT)
                .description("비용 절감 — 마케팅비 100만원 감소, 직원 사기 -0.1")
                .effectMap(Map.of("fixedCost", -100.0, "staffMorale", -0.1))
                .build(),
            Decision.builder()
                .decisionId("DEC_SERVICE_IMPROVE")
                .decisionType(Decision.DecisionType.SERVICE_IMPROVE)
                .description("서비스 개선 — 투자비 200만원, 만족도 +0.2, 재진율 +0.02")
                .effectMap(Map.of("fixedCost", 200.0, "satisfaction", 0.2, "returnRate", 0.02))
                .build()
        );
    }

    private List<SimulationEvent> getEventsForMonth(List<SimulationEvent> events, int month) {
        return events.stream()
                .filter(e -> e.getTriggerMonth() == month)
                .collect(Collectors.toList());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 일괄 계산 (기존 유지)
    // ═══════════════════════════════════════════════════════════════════════════

    private SimulationResult runSimulation(SimulationRequest req, List<SimulationEvent> events) {

        int    basePatientsPerDay = getBasePatients(req.getDeptCategory());
        double areaFactor         = getAreaFactor(req.getRegionSiGun());
        double revenuePerPatient  = getRevenuePerPatient(req.getDeptCategory());

        long laborCost        = computeLaborCost(req);
        long insuranceCost    = Math.round(laborCost * INSURANCE_RATE);
        long rentCost         = req.getMonthlyRent()      != null ? req.getMonthlyRent()      : 0L;
        long marketingCost    = req.getMonthlyMarketing() != null ? req.getMonthlyMarketing() : 0L;
        long depreciationCost = req.getInitialInvestment() / DEPRECIATION_MONTHS;
        long baseFixedMonthly = laborCost + insuranceCost + rentCost + marketingCost + depreciationCost;

        boolean hasLoan = req.getLoanAmount() != null && req.getLoanAmount() > 0
                       && req.getLoanMonths() != null && req.getLoanRate()   != null;
        long monthlyPrincipal = hasLoan ? req.getLoanAmount() / req.getLoanMonths() : 0L;

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

        long totalLaborCost    = 0L;
        long totalInsurance    = 0L;
        long totalRent         = 0L;
        long totalMarketing    = 0L;
        long totalDepreciation = 0L;
        long totalOtherMgmt    = 0L;
        long totalVariableCost = 0L;
        long totalInterest     = 0L;

        double reputation   = SimulationConfig.INITIAL_REPUTATION;
        double satisfaction = 3.0;
        double returnRate   = SimulationConfig.INITIAL_RETURN_RATE;
        double staffMorale  = 0.8;
        boolean isBankrupt  = false;

        for (int month = 1; month <= 36; month++) {

            List<String> monthEvents   = new ArrayList<>();
            double       revMultiplier = 1.0;
            long         extraCost     = 0L;
            for (SimulationEvent ev : events) {
                if (ev.getTriggerMonth() == month) {
                    monthEvents.add(ev.getEventId());
                    Map<String, Double> imp = ev.getImpactMap();
                    if (imp.containsKey("reputation"))   reputation   = clamp(reputation   + imp.get("reputation"),   0, 5);
                    if (imp.containsKey("satisfaction")) satisfaction = clamp(satisfaction + imp.get("satisfaction"), 0, 5);
                    if (imp.containsKey("returnRate"))   returnRate   = clamp(returnRate   + imp.get("returnRate"),   0, 1);
                    if (imp.containsKey("staffMorale"))  staffMorale  = clamp(staffMorale  + imp.get("staffMorale"),  0, 1);
                    if (imp.containsKey("revenue"))      revMultiplier += imp.get("revenue");
                    if (imp.containsKey("extraCost"))    extraCost    += Math.round(imp.get("extraCost"));
                }
            }

            double growth   = Math.min(1.0, 0.3 + month * 0.03);
            int    patients = (int)(basePatientsPerDay * WORKING_DAYS * areaFactor * growth);
            if (firstPatients == -1) firstPatients = patients;
            lastPatients = patients;

            long revenue       = Math.round(patients * revenuePerPatient * revMultiplier);
            long variableCost  = Math.round(revenue * VARIABLE_COST_RATIO);
            long otherMgmtCost = Math.round(revenue * OTHER_MGMT_RATIO);
            long fixedMonthly  = baseFixedMonthly + otherMgmtCost + extraCost;
            long operatingProfit = revenue - variableCost - fixedMonthly;

            long interest = 0L, principalPayment = 0L;
            if (hasLoan && month <= req.getLoanMonths()) {
                long remainingLoan = req.getLoanAmount() - (monthlyPrincipal * (month - 1));
                interest           = Math.round(remainingLoan * (req.getLoanRate() / 100.0) / 12.0);
                principalPayment   = monthlyPrincipal;
            }

            long tax       = operatingProfit > 0 ? Math.round(operatingProfit * TAX_RATE) : 0L;
            long netProfit = operatingProfit - interest - tax;

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

            totalLaborCost    += laborCost;
            totalInsurance    += insuranceCost;
            totalRent         += rentCost;
            totalMarketing    += marketingCost;
            totalDepreciation += depreciationCost;
            totalOtherMgmt    += otherMgmtCost;
            totalVariableCost += variableCost;
            totalInterest     += interest;

            if (bepMonth == -1 && netProfit > 0) bepMonth = month;

            double moraleDelta = (operatingProfit > 0) ? 0.005 : -0.02;
            staffMorale = clamp(staffMorale + moraleDelta, 0, 1);

            double satDelta = (staffMorale - 0.6) * 0.05;
            if (operatingProfit < 0) satDelta -= 0.03;
            satisfaction = clamp(satisfaction + satDelta, 0, 5);

            reputation = clamp(reputation + (satisfaction - reputation) * 0.08, 0, 5);

            double rrTarget = reputation / 10.0;
            returnRate = clamp(returnRate + (rrTarget - returnRate) * 0.05, 0, 1);

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

        double fixedCostRatio    = totalRevenue > 0 ? (double) totalFixed / totalRevenue * 100 : 0;
        double patientGrowthRate = firstPatients > 0
                ? ((double)(lastPatients - firstPatients) / firstPatients) * 100 : 0;

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
                .bepTargetRevenue(calcBepTargetRevenue(baseFixedMonthly))
                .patientGrowthRate(Math.round(patientGrowthRate * 10) / 10.0)
                .marketingCpa(calcCpa(req))
                .cumulativeRevenue(cumRevList)
                .cumulativeCost(cumCostList)
                .monthly(monthlyList)
                .kpiMessages(generateKpiMessages(bepMonth, cashBalance, fixedCostRatio, patientGrowthRate, totalFixed / 36))
                .finalReputation(round2(reputation))
                .finalSatisfaction(round2(satisfaction))
                .finalReturnRate(round3(returnRate))
                .costBreakdown(costBreakdown)
                .isBankrupt(isBankrupt)
                .grade(calcGrade(isBankrupt, bepMonth, reputation, cashBalance))
                .build();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 공통 헬퍼
    // ═══════════════════════════════════════════════════════════════════════════

    private long computeLaborCost(SimulationRequest req) {
        if (req.getStaffList() == null) return 0L;
        return req.getStaffList().stream().mapToLong(s -> s.getSalary() * s.getCount()).sum();
    }

    private String calcGrade(boolean isBankrupt, int bepMonth, double finalRep, long finalCash) {
        if (isBankrupt || bepMonth == -1)                              return "F";
        if (bepMonth <= 6  && finalRep >= 4.0 && finalCash > 0)       return "S";
        if (bepMonth <= 12 && finalRep >= 3.5)                         return "A";
        if (bepMonth <= 18)                                             return "B";
        return "C";
    }

    private SimulationRequest buildMockRequest() {
        SimulationRequest req = new SimulationRequest();
        req.setDeptCategory("내과");
        req.setRegionSiGun("강남구");
        req.setInitialInvestment(30000L);
        req.setLoanAmount(15000L);
        req.setLoanRate(4.5);
        req.setLoanMonths(36);
        req.setMonthlyRent(500L);
        req.setMonthlyMarketing(200L);

        StaffRequest doctor = new StaffRequest();
        doctor.setRole("의사");     doctor.setSalary(1000L); doctor.setCount(1);
        StaffRequest nurse  = new StaffRequest();
        nurse.setRole("간호사");    nurse.setSalary(300L);  nurse.setCount(2);
        StaffRequest admin  = new StaffRequest();
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
        List<KpiMessage> msgs = new ArrayList<>();
        long threeMonthOpex = avgFixed * 3;

        if      (bepMonth >= 1 && bepMonth <= 6)  msgs.add(new KpiMessage("평균보다 빠른 흑자 전환 — 비용 구조 및 매출 전략 효율적", "success"));
        else if (bepMonth >= 7 && bepMonth <= 12) msgs.add(new KpiMessage("업계 평균 수준의 손익분기 달성", "warning"));
        else                                       msgs.add(new KpiMessage("손익분기 지연 — 고정비 구조 또는 매출 전략 전면 재검토 필요", "danger"));

        if      (fixedRatio <= 40) msgs.add(new KpiMessage("고정비 비율 우수 — 수익 구조 안정적", "success"));
        else if (fixedRatio <= 59) msgs.add(new KpiMessage("고정비 구조 적정 수준", "warning"));
        else                       msgs.add(new KpiMessage("고정비 과부하 — 임대료/인건비 구조 재검토 필요", "danger"));

        if      (finalCash > threeMonthOpex) msgs.add(new KpiMessage("현금 안정적 — 충분한 운전자본 확보", "success"));
        else if (finalCash > 0)              msgs.add(new KpiMessage("운전자본 부족 — 예상치 못한 지출 발생 시 위기 가능", "warning"));
        else                                 msgs.add(new KpiMessage("현금 고갈 위험 — 즉시 비용 절감 또는 운전자본 확보 필요", "danger"));

        if      (growthRate >= 5) msgs.add(new KpiMessage("환자수 성장세 우수 — 마케팅 및 리텐션 전략 성공적", "success"));
        else if (growthRate >= 0) msgs.add(new KpiMessage("환자수 성장 정체 — 마케팅 채널 재점검 필요", "warning"));
        else                      msgs.add(new KpiMessage("환자수 이탈 심화 — 원인 분석 및 긴급 대응 필요", "danger"));

        return msgs;
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
        if (region.contains("강남") || region.contains("서초") || region.contains("송파")) return 1.0;
        if (region.contains("노원") || region.contains("도봉") || region.contains("중랑"))  return 0.60;
        return 0.78;
    }

    private double clamp(double v, double min, double max) { return Math.max(min, Math.min(max, v)); }
    private double round2(double v) { return Math.round(v * 100)  / 100.0; }
    private double round3(double v) { return Math.round(v * 1000) / 1000.0; }
}

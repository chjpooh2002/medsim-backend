package com.medsim.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medsim.backend.domain.*;
import com.medsim.backend.dto.request.SimulationRequest;
import com.medsim.backend.dto.request.StaffRequest;
import com.medsim.backend.dto.response.KpiMessage;
import com.medsim.backend.dto.response.SimulationResult;
import com.medsim.backend.dto.response.SimulationTurnResult;
import com.medsim.backend.repository.SimulationStateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SimulationService {

    // ── 재무 상수 (기존 유지) ─────────────────────────────────────────────────────
    private static final int    WORKING_DAYS        = 22;
    private static final double VARIABLE_COST_RATIO = 0.15;
    private static final double OTHER_MGMT_RATIO    = 0.02;
    private static final double TAX_RATE            = 0.20;
    private static final double INSURANCE_RATE      = 0.106;
    private static final int    DEPRECIATION_MONTHS = 60;

    // ── DB 영구 저장 + 직렬화 ────────────────────────────────────────────────────
    private final SimulationStateRepository simulationStateRepository;
    private final ObjectMapper objectMapper;

    // ── 인메모리 캐시 (성능용 write-through) ──────────────────────────────────────
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
                .pendingEvents(new ArrayList<>(generateEventsForSimulation(simId)))
                .consecutiveLossMonths(0)
                .selectedEventResponses(new HashMap<>())
                .build();

        MonthlyData monthData = runOneTurn(state, Collections.emptyList());
        advanceState(state, 1);
        simulationStore.put(simId, state);
        persistState(simId, state);

        return buildTurnResult(simId, 1, monthData, state);
    }

    /**
     * 의사결정을 제출하고 다음 달을 실행한다.
     *
     * @param simulationId startSimulation() 이 반환한 ID
     * @param decisionIds  이번 달 적용할 Decision ID 목록 (빈 리스트 가능)
     */
    public SimulationTurnResult nextTurn(String simulationId, List<String> decisionIds) {
        SimulationState state = loadState(simulationId);
        if (state == null) {
            throw new IllegalArgumentException("존재하지 않는 시뮬레이션입니다: " + simulationId);
        }
        if (state.isCompleted() || state.isBankrupt()) {
            return SimulationTurnResult.builder()
                    .simulationId(simulationId)
                    .currentMonth(state.getCurrentMonth())
                    .isBankrupt(state.isBankrupt())
                    .isCompleted(state.isCompleted())
                    .bankruptReason(state.getBankruptReason())
                    .bankruptMonth(state.getBankruptMonth())
                    .nextEvents(Collections.emptyList())
                    .availableDecisions(Collections.emptyList())
                    .finalResult(buildFinalResult(state))
                    .build();
        }

        List<Decision> selected = getAvailableDecisions().stream()
                .filter(d -> decisionIds.contains(d.getDecisionId()))
                .collect(Collectors.toList());

        int thisTurnMonth = state.getCurrentMonth();
        MonthlyData monthData = runOneTurn(state, selected);
        advanceState(state, thisTurnMonth);

        simulationStore.put(simulationId, state);
        persistState(simulationId, state);

        return buildTurnResult(simulationId, thisTurnMonth, monthData, state);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 상태 저장/조회 (인메모리 캐시 + DB write-through)
    // ═══════════════════════════════════════════════════════════════════════════

    /** 인메모리 캐시 우선 조회 → miss 시 DB fallback 후 캐시에 저장 */
    private SimulationState loadState(String simulationId) {
        SimulationState cached = simulationStore.get(simulationId);
        if (cached != null) return cached;

        return simulationStateRepository.findBySimulationId(simulationId)
                .map(entity -> {
                    try {
                        SimulationState state = objectMapper.readValue(entity.getStateJson(), SimulationState.class);
                        simulationStore.put(simulationId, state);
                        return state;
                    } catch (Exception e) {
                        throw new RuntimeException("SimulationState 역직렬화 실패: " + simulationId, e);
                    }
                })
                .orElse(null);
    }

    /** 상태를 JSON으로 직렬화해 DB에 저장 (실패해도 인메모리는 유효) */
    private void persistState(String simulationId, SimulationState state) {
        try {
            String json = objectMapper.writeValueAsString(state);
            SimulationStateEntity entity = SimulationStateEntity.builder()
                    .simulationId(simulationId)
                    .stateJson(json)
                    .currentMonth(state.getCurrentMonth())
                    .isCompleted(state.isCompleted())
                    .isBankrupt(state.isBankrupt())
                    .build();
            simulationStateRepository.save(entity);
        } catch (Exception e) {
            System.err.println("[SimulationService] DB 저장 실패 — " + simulationId + " / " + e.getMessage());
        }
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

        // ② 이벤트 효과 + 선택한 대응 옵션 효과 적용
        List<SimulationEvent> activeEventIds = new ArrayList<>();
        Map<String, String> selectedResponses = state.getSelectedEventResponses() != null
                ? state.getSelectedEventResponses() : Collections.emptyMap();

        for (SimulationEvent ev : state.getPendingEvents()) {
            if (ev.getTriggerMonth() == month) {
                activeEventIds.add(ev);
                Map<String, Double> imp = ev.getImpactMap();
                if (imp.containsKey("revenue"))      revMultiplier += imp.get("revenue");
                if (imp.containsKey("extraCost"))    extraCost     += Math.round(imp.get("extraCost"));
                if (imp.containsKey("reputation"))   state.setReputationScore(clamp(state.getReputationScore()   + imp.get("reputation"),   0, 5));
                if (imp.containsKey("satisfaction")) state.setSatisfactionScore(clamp(state.getSatisfactionScore() + imp.get("satisfaction"), 0, 5));
                if (imp.containsKey("returnRate"))   state.setReturnPatientRate(clamp(state.getReturnPatientRate() + imp.get("returnRate"),   0, 1));
                if (imp.containsKey("staffMorale"))  state.setStaffMorale(clamp(state.getStaffMorale()           + imp.get("staffMorale"),   0, 1));

                // 선택한 대응 옵션 효과 추가 반영
                String selectedOptionId = selectedResponses.get(ev.getEventId());
                if (selectedOptionId != null && ev.getResponseOptions() != null) {
                    for (SimulationEvent.EventResponseOption opt : ev.getResponseOptions()) {
                        if (selectedOptionId.equals(opt.getOptionId()) && opt.getEffectMap() != null) {
                            Map<String, Double> eff = opt.getEffectMap();
                            if (eff.containsKey("revenue"))      revMultiplier += eff.get("revenue");
                            if (eff.containsKey("extraCost"))    extraCost     += Math.round(eff.get("extraCost"));
                            if (eff.containsKey("reputation"))   state.setReputationScore(clamp(state.getReputationScore()   + eff.get("reputation"),   0, 5));
                            if (eff.containsKey("satisfaction")) state.setSatisfactionScore(clamp(state.getSatisfactionScore() + eff.get("satisfaction"), 0, 5));
                            if (eff.containsKey("returnRate"))   state.setReturnPatientRate(clamp(state.getReturnPatientRate() + eff.get("returnRate"),   0, 1));
                            if (eff.containsKey("staffMorale"))  state.setStaffMorale(clamp(state.getStaffMorale()           + eff.get("staffMorale"),   0, 1));
                            break;
                        }
                    }
                }
            }
        }

        // 처리 완료된 이벤트의 대응 선택 초기화
        if (state.getSelectedEventResponses() != null) {
            for (SimulationEvent ev : activeEventIds) {
                state.getSelectedEventResponses().remove(ev.getEventId());
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

        // ─ 파산 조건 1: 현금 소진
        if (state.getCashBalance() <= 0 && !state.isBankrupt()) {
            state.setBankrupt(true);
            state.setBankruptReason("현금 소진");
            state.setBankruptMonth(month);
        }

        // ─ 파산 조건 2: 3개월 연속 순손실 + 현금 < 월 고정비
        if (netProfit < 0) {
            state.setConsecutiveLossMonths(state.getConsecutiveLossMonths() + 1);
        } else {
            state.setConsecutiveLossMonths(0);
        }
        if (!state.isBankrupt() && state.getConsecutiveLossMonths() >= 3
                && state.getCashBalance() < baseFixedMonthly) {
            state.setBankrupt(true);
            state.setBankruptReason("3개월 연속 적자로 인한 자금 고갈");
            state.setBankruptMonth(month);
        }

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

        // ⑪ 신규/재진 분할
        int returnPatientCount = (month == 1) ? 0 : (int)(patients * state.getReturnPatientRate());
        int newPatientCount    = patients - returnPatientCount;

        // ⑫ 비용 세부 Map
        Map<String, Long> monthCostBreakdown = new LinkedHashMap<>();
        monthCostBreakdown.put("인건비",    laborCost);
        monthCostBreakdown.put("4대보험",   insuranceCost);
        monthCostBreakdown.put("임대료",    rentCost);
        monthCostBreakdown.put("마케팅비",  marketingCost);
        monthCostBreakdown.put("감가상각비", depreciationCost);
        monthCostBreakdown.put("기타관리비", otherMgmtCost);
        monthCostBreakdown.put("변동비",    variableCost);
        monthCostBreakdown.put("이자비용",  interest);

        // ⑬ 전월 대비 증감율
        List<MonthlyData> history = state.getMonthlyHistory();
        double cashChangeRate       = 0.0;
        double patientsChangeRate   = 0.0;
        double reputationChangeRate = 0.0;
        if (!history.isEmpty()) {
            MonthlyData prev = history.get(history.size() - 1);
            long   prevCash  = prev.getCumulativeCash()  != null ? prev.getCumulativeCash()  : 0L;
            int    prevPats  = prev.getPatientsCount()   != null ? prev.getPatientsCount()   : 0;
            double prevRep   = prev.getReputationScore() != null ? prev.getReputationScore() : 0.0;
            if (prevCash > 0) cashChangeRate       = round4((double)(state.getCashBalance() - prevCash) / prevCash);
            if (prevPats > 0) patientsChangeRate   = round4((double)(patients - prevPats) / prevPats);
            if (prevRep  > 0) reputationChangeRate = round4((state.getReputationScore() - prevRep) / prevRep);
        }

        MonthlyData data = MonthlyData.builder()
                .month(month)
                .patientsCount(patients)
                .newPatientCount(newPatientCount)
                .returnPatientCount(returnPatientCount)
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
                .cashChangeRate(cashChangeRate)
                .patientsChangeRate(patientsChangeRate)
                .reputationChangeRate(reputationChangeRate)
                .reputationScore(round2(state.getReputationScore()))
                .patientSatisfaction(round2(state.getSatisfactionScore()))
                .returnPatientRate(round3(state.getReturnPatientRate()))
                .staffMorale(round3(state.getStaffMorale()))
                .laborCost(laborCost)
                .insuranceCost(insuranceCost)
                .rentCost(rentCost)
                .marketingCost(marketingCost)
                .depreciationCost(depreciationCost)
                .otherMgmtCost(otherMgmtCost)
                .costBreakdown(monthCostBreakdown)
                .activeEvents(activeEventIds)
                .appliedDecisions(decisions)
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
                .simulationId(simId)
                .currentMonth(executedMonth)
                .monthlyData(monthData)
                .nextEvents(nextEvents)
                .availableDecisions(done ? Collections.emptyList() : getAvailableDecisions())
                .isBankrupt(state.isBankrupt())
                .isCompleted(state.isCompleted())
                .bankruptReason(state.getBankruptReason())
                .bankruptMonth(state.getBankruptMonth())
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

    /**
     * 이벤트 대응 옵션 선택 — 다음 턴 진행 시 효과가 반영된다.
     */
    public Map<String, String> applyEventResponse(String simulationId, String eventId, String optionId) {
        SimulationState state = loadState(simulationId);
        if (state == null) throw new IllegalArgumentException("존재하지 않는 시뮬레이션입니다: " + simulationId);
        if (state.isCompleted() || state.isBankrupt()) throw new IllegalStateException("이미 종료된 시뮬레이션입니다.");

        int nextMonth = state.getCurrentMonth();
        boolean valid = state.getPendingEvents().stream()
                .anyMatch(ev -> ev.getEventId().equals(eventId) && ev.getTriggerMonth() == nextMonth);
        if (!valid) throw new IllegalArgumentException("해당 월(" + nextMonth + ")에 해당 이벤트가 없습니다: " + eventId);

        if (state.getSelectedEventResponses() == null) state.setSelectedEventResponses(new HashMap<>());
        state.getSelectedEventResponses().put(eventId, optionId);

        simulationStore.put(simulationId, state);
        persistState(simulationId, state);

        return Map.of("status", "ok", "eventId", eventId, "optionId", optionId);
    }

    /**
     * simId 기반 시드로 6개월 주기 이벤트를 생성한다 (재현 가능).
     * 6/12/18/24/30/36개월에 각 1~2개 이벤트 배정.
     */
    private List<SimulationEvent> generateEventsForSimulation(String simId) {
        long seed = simId.hashCode();
        Random rng = new Random(seed);

        List<SimulationEvent> pool = buildEventPool();
        List<SimulationEvent> negPool = pool.stream()
                .filter(e -> e.getEventType() == SimulationEvent.EventType.COMPETITOR_OPEN
                          || e.getEventType() == SimulationEvent.EventType.RENT_INCREASE
                          || e.getEventType() == SimulationEvent.EventType.TRAFFIC_DECREASE
                          || e.getEventType() == SimulationEvent.EventType.STAFF_QUIT
                          || e.getEventType() == SimulationEvent.EventType.MEDICAL_ACCIDENT
                          || e.getEventType() == SimulationEvent.EventType.EQUIPMENT_BREAK
                          || e.getEventType() == SimulationEvent.EventType.REVIEW_DROP
                          || e.getEventType() == SimulationEvent.EventType.COMPLAINT)
                .collect(Collectors.toList());
        List<SimulationEvent> posPool = pool.stream()
                .filter(e -> e.getEventType() == SimulationEvent.EventType.REVIEW_VIRAL
                          || e.getEventType() == SimulationEvent.EventType.LOCAL_REFERRAL
                          || e.getEventType() == SimulationEvent.EventType.CHECKUP_CONTRACT
                          || e.getEventType() == SimulationEvent.EventType.FLU_SEASON)
                .collect(Collectors.toList());

        int[]    checkpoints = {6, 12, 18, 24, 30, 36};
        double[] negRatios   = {0.5, 0.5, 0.6, 0.6, 0.5, 0.5};

        List<SimulationEvent> result = new ArrayList<>();
        for (int i = 0; i < checkpoints.length; i++) {
            int    month    = checkpoints[i];
            double negRatio = negRatios[i];
            int    count    = rng.nextDouble() < 0.3 ? 2 : 1;

            Set<String> usedAtMonth = new HashSet<>();
            for (int j = 0; j < count; j++) {
                boolean useNeg = rng.nextDouble() < negRatio;
                List<SimulationEvent> candidates = useNeg ? negPool : posPool;
                List<SimulationEvent> available  = candidates.stream()
                        .filter(e -> !usedAtMonth.contains(e.getEventType().name()))
                        .collect(Collectors.toList());
                if (available.isEmpty()) available = candidates;

                SimulationEvent tmpl = available.get(rng.nextInt(available.size()));
                usedAtMonth.add(tmpl.getEventType().name());

                result.add(SimulationEvent.builder()
                        .eventId("EVT_" + tmpl.getEventType().name() + "_M" + month + "_" + j)
                        .triggerMonth(month)
                        .eventType(tmpl.getEventType())
                        .category(tmpl.getCategory())
                        .description(tmpl.getDescription())
                        .impactMap(tmpl.getImpactMap())
                        .responseOptions(tmpl.getResponseOptions())
                        .build());
            }
        }
        return result;
    }

    /** 12개 이벤트 템플릿 풀 (4 카테고리 × 3) */
    private List<SimulationEvent> buildEventPool() {
        List<SimulationEvent> pool = new ArrayList<>();

        // ── MARKET ──────────────────────────────────────────────────────────
        pool.add(SimulationEvent.builder()
            .eventType(SimulationEvent.EventType.COMPETITOR_OPEN)
            .category(SimulationEvent.EventCategory.MARKET)
            .description("인근에 경쟁 병원 개원 — 신환 유입 감소 및 평판 하락")
            .impactMap(Map.of("revenue", -0.10, "reputation", -0.1))
            .responseOptions(Arrays.asList(
                SimulationEvent.EventResponseOption.builder()
                    .optionId("RESP_COMPETITOR_AD").label("광고 확대")
                    .description("광고비 200만원 추가로 신환 유입 회복 시도")
                    .effectMap(Map.of("revenue", 0.09, "extraCost", 200.0)).build(),
                SimulationEvent.EventResponseOption.builder()
                    .optionId("RESP_COMPETITOR_QUALITY").label("상담 품질 강화")
                    .description("상담 교육 100만원 투자, 평판·만족도 회복")
                    .effectMap(Map.of("reputation", 0.2, "satisfaction", 0.15, "extraCost", 100.0)).build(),
                SimulationEvent.EventResponseOption.builder()
                    .optionId("RESP_COMPETITOR_PRICE").label("가격 경쟁")
                    .description("가격 인하로 환자 유지 시도, 마진 소폭 감소")
                    .effectMap(Map.of("revenue", 0.05)).build(),
                SimulationEvent.EventResponseOption.builder()
                    .optionId("RESP_COMPETITOR_WAIT").label("현재 전략 유지")
                    .description("별도 대응 없이 현재 전략 유지")
                    .effectMap(Map.of()).build()
            )).build());

        pool.add(SimulationEvent.builder()
            .eventType(SimulationEvent.EventType.RENT_INCREASE)
            .category(SimulationEvent.EventCategory.MARKET)
            .description("임대인 임대료 인상 통보 — 월 고정비 150만원 증가")
            .impactMap(Map.of("extraCost", 150.0))
            .responseOptions(Arrays.asList(
                SimulationEvent.EventResponseOption.builder()
                    .optionId("RESP_RENT_NEGOTIATE").label("임대인 협상")
                    .description("협상을 통해 인상분 100만원 절감")
                    .effectMap(Map.of("extraCost", -100.0)).build(),
                SimulationEvent.EventResponseOption.builder()
                    .optionId("RESP_RENT_REVENUE").label("수익 강화")
                    .description("비용 흡수를 위한 수익 확대 전략, 매출 +5%")
                    .effectMap(Map.of("revenue", 0.05)).build(),
                SimulationEvent.EventResponseOption.builder()
                    .optionId("RESP_RENT_CUTCOST").label("다른 비용 절감")
                    .description("기타 비용 50만원 삭감, 사기 소폭 하락")
                    .effectMap(Map.of("extraCost", -50.0, "staffMorale", -0.05)).build(),
                SimulationEvent.EventResponseOption.builder()
                    .optionId("RESP_RENT_ACCEPT").label("수용")
                    .description("인상을 그대로 수용")
                    .effectMap(Map.of()).build()
            )).build());

        pool.add(SimulationEvent.builder()
            .eventType(SimulationEvent.EventType.TRAFFIC_DECREASE)
            .category(SimulationEvent.EventCategory.MARKET)
            .description("상권 변화로 유동인구 감소 — 매출 8% 하락")
            .impactMap(Map.of("revenue", -0.08))
            .responseOptions(Arrays.asList(
                SimulationEvent.EventResponseOption.builder()
                    .optionId("RESP_TRAFFIC_MARKETING").label("마케팅 강화")
                    .description("광고비 150만원 추가로 원거리 환자 유치")
                    .effectMap(Map.of("revenue", 0.06, "extraCost", 150.0)).build(),
                SimulationEvent.EventResponseOption.builder()
                    .optionId("RESP_TRAFFIC_ONLINE").label("온라인 채널 확대")
                    .description("온라인 예약·홍보 80만원 투자, 매출 회복 + 평판 향상")
                    .effectMap(Map.of("revenue", 0.04, "reputation", 0.1, "extraCost", 80.0)).build(),
                SimulationEvent.EventResponseOption.builder()
                    .optionId("RESP_TRAFFIC_SPECIALIZE").label("전문화 강화")
                    .description("특화 진료 강화로 충성 환자 유지")
                    .effectMap(Map.of("revenue", 0.03, "satisfaction", 0.1)).build(),
                SimulationEvent.EventResponseOption.builder()
                    .optionId("RESP_TRAFFIC_WAIT").label("관망")
                    .description("상권 회복을 기대하며 대기")
                    .effectMap(Map.of()).build()
            )).build());

        // ── OPERATION ────────────────────────────────────────────────────────
        pool.add(SimulationEvent.builder()
            .eventType(SimulationEvent.EventType.STAFF_QUIT)
            .category(SimulationEvent.EventCategory.OPERATION)
            .description("핵심 직원 갑작스러운 퇴사 — 채용비 100만원, 매출·사기 하락")
            .impactMap(Map.of("staffMorale", -0.15, "extraCost", 100.0, "revenue", -0.05))
            .responseOptions(Arrays.asList(
                SimulationEvent.EventResponseOption.builder()
                    .optionId("RESP_STAFFQUIT_RECRUIT").label("즉시 채용")
                    .description("채용비 150만원 추가 투자, 빠른 공백 해소")
                    .effectMap(Map.of("extraCost", 150.0, "revenue", 0.04, "staffMorale", 0.1)).build(),
                SimulationEvent.EventResponseOption.builder()
                    .optionId("RESP_STAFFQUIT_BONUS").label("남은 직원 인센티브")
                    .description("80만원 인센티브로 기존 직원 사기 회복")
                    .effectMap(Map.of("extraCost", 80.0, "staffMorale", 0.15)).build(),
                SimulationEvent.EventResponseOption.builder()
                    .optionId("RESP_STAFFQUIT_OUTSOURCE").label("외부 위탁")
                    .description("50만원 위탁비로 업무 일부 커버")
                    .effectMap(Map.of("extraCost", 50.0, "revenue", 0.02)).build(),
                SimulationEvent.EventResponseOption.builder()
                    .optionId("RESP_STAFFQUIT_WAIT").label("채용 보류")
                    .description("당분간 기존 인원으로 운영")
                    .effectMap(Map.of()).build()
            )).build());

        pool.add(SimulationEvent.builder()
            .eventType(SimulationEvent.EventType.MEDICAL_ACCIDENT)
            .category(SimulationEvent.EventCategory.OPERATION)
            .description("의료 사고 발생 — 평판 급락, 합의금 300만원, 만족도 하락")
            .impactMap(Map.of("reputation", -0.4, "extraCost", 300.0, "satisfaction", -0.3))
            .responseOptions(Arrays.asList(
                SimulationEvent.EventResponseOption.builder()
                    .optionId("RESP_ACCIDENT_LEGAL").label("법적 대응 + 보상")
                    .description("200만원 추가 비용으로 신속 합의, 평판 일부 회복")
                    .effectMap(Map.of("extraCost", 200.0, "reputation", 0.1)).build(),
                SimulationEvent.EventResponseOption.builder()
                    .optionId("RESP_ACCIDENT_PR").label("대외 홍보 관리")
                    .description("150만원 PR 투자로 부정 여론 최소화")
                    .effectMap(Map.of("extraCost", 150.0, "reputation", 0.15, "satisfaction", 0.1)).build(),
                SimulationEvent.EventResponseOption.builder()
                    .optionId("RESP_ACCIDENT_PROTOCOL").label("안전 프로토콜 강화")
                    .description("50만원 교육 투자, 재발 방지 + 평판 소폭 회복")
                    .effectMap(Map.of("extraCost", 50.0, "reputation", 0.05, "satisfaction", 0.05)).build(),
                SimulationEvent.EventResponseOption.builder()
                    .optionId("RESP_ACCIDENT_MINIMIZE").label("최소 대응")
                    .description("별도 비용 없이 사태 수습 대기")
                    .effectMap(Map.of()).build()
            )).build());

        pool.add(SimulationEvent.builder()
            .eventType(SimulationEvent.EventType.EQUIPMENT_BREAK)
            .category(SimulationEvent.EventCategory.OPERATION)
            .description("핵심 의료 장비 고장 — 수리비 250만원, 진료 차질로 매출 하락")
            .impactMap(Map.of("extraCost", 250.0, "revenue", -0.05))
            .responseOptions(Arrays.asList(
                SimulationEvent.EventResponseOption.builder()
                    .optionId("RESP_EQUIP_REPAIR").label("즉시 수리")
                    .description("추가 100만원으로 긴급 수리, 빠른 정상화")
                    .effectMap(Map.of("extraCost", 100.0, "revenue", 0.04)).build(),
                SimulationEvent.EventResponseOption.builder()
                    .optionId("RESP_EQUIP_RENT").label("장비 임대")
                    .description("월 80만원 임대 장비로 진료 유지")
                    .effectMap(Map.of("extraCost", 80.0, "revenue", 0.03)).build(),
                SimulationEvent.EventResponseOption.builder()
                    .optionId("RESP_EQUIP_PARTIAL").label("부분 진료 유지")
                    .description("장비 불요 진료만 유지, 추가 비용 30만원")
                    .effectMap(Map.of("extraCost", 30.0, "revenue", 0.01)).build(),
                SimulationEvent.EventResponseOption.builder()
                    .optionId("RESP_EQUIP_WAIT").label("수리 대기")
                    .description("제조사 수리 일정 대기, 추가 비용 없음")
                    .effectMap(Map.of()).build()
            )).build());

        // ── PATIENT ──────────────────────────────────────────────────────────
        pool.add(SimulationEvent.builder()
            .eventType(SimulationEvent.EventType.REVIEW_DROP)
            .category(SimulationEvent.EventCategory.PATIENT)
            .description("포털 부정 리뷰 급증 — 평판 하락, 신환 감소")
            .impactMap(Map.of("reputation", -0.3, "revenue", -0.07))
            .responseOptions(Arrays.asList(
                SimulationEvent.EventResponseOption.builder()
                    .optionId("RESP_REVIEW_RESPONSE").label("리뷰 적극 응대")
                    .description("50만원 투자로 부정 리뷰 대응 + 만족도 개선")
                    .effectMap(Map.of("reputation", 0.2, "satisfaction", 0.1, "extraCost", 50.0)).build(),
                SimulationEvent.EventResponseOption.builder()
                    .optionId("RESP_REVIEW_IMPROVE").label("서비스 개선")
                    .description("80만원 투자로 근본적 서비스 품질 향상")
                    .effectMap(Map.of("satisfaction", 0.2, "reputation", 0.1, "extraCost", 80.0)).build(),
                SimulationEvent.EventResponseOption.builder()
                    .optionId("RESP_REVIEW_AD").label("긍정 리뷰 캠페인")
                    .description("120만원으로 긍정 리뷰 유도 캠페인 운영")
                    .effectMap(Map.of("reputation", 0.15, "extraCost", 120.0)).build(),
                SimulationEvent.EventResponseOption.builder()
                    .optionId("RESP_REVIEW_IGNORE").label("무대응")
                    .description("별도 조치 없이 자연 회복 대기")
                    .effectMap(Map.of()).build()
            )).build());

        pool.add(SimulationEvent.builder()
            .eventType(SimulationEvent.EventType.REVIEW_VIRAL)
            .category(SimulationEvent.EventCategory.PATIENT)
            .description("포털 긍정 리뷰 바이럴 — 신환 급증 및 평판 대폭 상승 [긍정]")
            .impactMap(Map.of("revenue", 0.15, "reputation", 0.4))
            .responseOptions(Arrays.asList(
                SimulationEvent.EventResponseOption.builder()
                    .optionId("RESP_VIRAL_AMPLIFY").label("홍보 증폭")
                    .description("100만원 추가 광고로 바이럴 효과 극대화")
                    .effectMap(Map.of("revenue", 0.05, "extraCost", 100.0)).build(),
                SimulationEvent.EventResponseOption.builder()
                    .optionId("RESP_VIRAL_CAPACITY").label("진료 역량 확대")
                    .description("150만원 투자로 증가한 환자 수용, 사기 소폭 하락")
                    .effectMap(Map.of("satisfaction", 0.1, "staffMorale", -0.05, "extraCost", 150.0)).build(),
                SimulationEvent.EventResponseOption.builder()
                    .optionId("RESP_VIRAL_PREMIUM").label("프리미엄 전환")
                    .description("80만원 투자로 고품질 서비스 강조, 매출·만족도 향상")
                    .effectMap(Map.of("revenue", 0.03, "satisfaction", 0.15, "extraCost", 80.0)).build(),
                SimulationEvent.EventResponseOption.builder()
                    .optionId("RESP_VIRAL_STANDARD").label("표준 대응")
                    .description("기본 운영 유지, 추가 비용 없음")
                    .effectMap(Map.of()).build()
            )).build());

        pool.add(SimulationEvent.builder()
            .eventType(SimulationEvent.EventType.COMPLAINT)
            .category(SimulationEvent.EventCategory.PATIENT)
            .description("환자 민원 급증 — 만족도·평판 동반 하락")
            .impactMap(Map.of("satisfaction", -0.2, "reputation", -0.15))
            .responseOptions(Arrays.asList(
                SimulationEvent.EventResponseOption.builder()
                    .optionId("RESP_COMPLAINT_RESOLVE").label("적극 해결")
                    .description("80만원 투자로 민원 신속 대응, 만족도·평판 회복")
                    .effectMap(Map.of("satisfaction", 0.15, "reputation", 0.1, "extraCost", 80.0)).build(),
                SimulationEvent.EventResponseOption.builder()
                    .optionId("RESP_COMPLAINT_TRAINING").label("직원 교육")
                    .description("60만원 교육으로 서비스 역량 향상")
                    .effectMap(Map.of("satisfaction", 0.1, "staffMorale", 0.05, "extraCost", 60.0)).build(),
                SimulationEvent.EventResponseOption.builder()
                    .optionId("RESP_COMPLAINT_PROCESS").label("프로세스 개선")
                    .description("40만원으로 접수·안내 프로세스 개선")
                    .effectMap(Map.of("satisfaction", 0.08, "reputation", 0.05, "extraCost", 40.0)).build(),
                SimulationEvent.EventResponseOption.builder()
                    .optionId("RESP_COMPLAINT_DENY").label("방어적 대응")
                    .description("민원에 소극적으로 대응")
                    .effectMap(Map.of()).build()
            )).build());

        // ── GROWTH ───────────────────────────────────────────────────────────
        pool.add(SimulationEvent.builder()
            .eventType(SimulationEvent.EventType.LOCAL_REFERRAL)
            .category(SimulationEvent.EventCategory.GROWTH)
            .description("지역 내 입소문 확산 — 매출 10% 상승, 평판 향상 [긍정]")
            .impactMap(Map.of("revenue", 0.10, "reputation", 0.2))
            .responseOptions(Arrays.asList(
                SimulationEvent.EventResponseOption.builder()
                    .optionId("RESP_REFERRAL_PROGRAM").label("추천 프로그램 도입")
                    .description("80만원 투자로 공식 추천인 제도 운영")
                    .effectMap(Map.of("revenue", 0.05, "reputation", 0.1, "extraCost", 80.0)).build(),
                SimulationEvent.EventResponseOption.builder()
                    .optionId("RESP_REFERRAL_PARTNER").label("지역 파트너 확대")
                    .description("120만원 투자로 약국·기관 협력 네트워크 구축")
                    .effectMap(Map.of("revenue", 0.08, "extraCost", 120.0)).build(),
                SimulationEvent.EventResponseOption.builder()
                    .optionId("RESP_REFERRAL_EVENT").label("감사 이벤트")
                    .description("60만원으로 기존 환자 감사 이벤트 진행")
                    .effectMap(Map.of("satisfaction", 0.15, "reputation", 0.1, "extraCost", 60.0)).build(),
                SimulationEvent.EventResponseOption.builder()
                    .optionId("RESP_REFERRAL_STANDARD").label("기본 유지")
                    .description("별도 투자 없이 현재 흐름 유지")
                    .effectMap(Map.of()).build()
            )).build());

        pool.add(SimulationEvent.builder()
            .eventType(SimulationEvent.EventType.CHECKUP_CONTRACT)
            .category(SimulationEvent.EventCategory.GROWTH)
            .description("기업·단체 검진 계약 성사 — 매출 18% 상승 [긍정]")
            .impactMap(Map.of("revenue", 0.18))
            .responseOptions(Arrays.asList(
                SimulationEvent.EventResponseOption.builder()
                    .optionId("RESP_CONTRACT_EXPAND").label("계약 확장")
                    .description("100만원 투자로 계약 규모 및 기업 수 확대")
                    .effectMap(Map.of("revenue", 0.05, "extraCost", 100.0)).build(),
                SimulationEvent.EventResponseOption.builder()
                    .optionId("RESP_CONTRACT_QUALITY").label("품질 강화")
                    .description("80만원 투자로 검진 품질 향상, 재계약률 상승")
                    .effectMap(Map.of("satisfaction", 0.1, "reputation", 0.1, "extraCost", 80.0)).build(),
                SimulationEvent.EventResponseOption.builder()
                    .optionId("RESP_CONTRACT_STAFF").label("인력 보강")
                    .description("150만원으로 검진 전담 인력 확충")
                    .effectMap(Map.of("staffMorale", 0.05, "extraCost", 150.0)).build(),
                SimulationEvent.EventResponseOption.builder()
                    .optionId("RESP_CONTRACT_STANDARD").label("표준 이행")
                    .description("계약 기본 사항만 이행")
                    .effectMap(Map.of()).build()
            )).build());

        pool.add(SimulationEvent.builder()
            .eventType(SimulationEvent.EventType.FLU_SEASON)
            .category(SimulationEvent.EventCategory.GROWTH)
            .description("독감 시즌 도래 — 환자 급증으로 매출 20% 상승, 직원 과로로 사기 하락 [긍정+부작용]")
            .impactMap(Map.of("revenue", 0.20, "staffMorale", -0.1))
            .responseOptions(Arrays.asList(
                SimulationEvent.EventResponseOption.builder()
                    .optionId("RESP_FLU_STAFF_UP").label("임시 인력 충원")
                    .description("200만원으로 임시 의료진 고용, 과로 해소")
                    .effectMap(Map.of("staffMorale", 0.15, "extraCost", 200.0)).build(),
                SimulationEvent.EventResponseOption.builder()
                    .optionId("RESP_FLU_HOURS").label("진료 시간 확대")
                    .description("50만원 추가로 연장 진료, 매출 극대화")
                    .effectMap(Map.of("revenue", 0.05, "staffMorale", -0.05, "extraCost", 50.0)).build(),
                SimulationEvent.EventResponseOption.builder()
                    .optionId("RESP_FLU_PREMIUM").label("독감 케어 패키지")
                    .description("60만원으로 독감 특화 케어 상품 운영")
                    .effectMap(Map.of("revenue", 0.03, "satisfaction", 0.1, "extraCost", 60.0)).build(),
                SimulationEvent.EventResponseOption.builder()
                    .optionId("RESP_FLU_STANDARD").label("표준 운영")
                    .description("추가 비용 없이 기본 운영 유지")
                    .effectMap(Map.of()).build()
            )).build());

        return pool;
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

            List<SimulationEvent> monthEvents   = new ArrayList<>();
            double                revMultiplier = 1.0;
            long                  extraCost     = 0L;
            for (SimulationEvent ev : events) {
                if (ev.getTriggerMonth() == month) {
                    monthEvents.add(ev);
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

            // 신규/재진 분할
            int retPats = (month == 1) ? 0 : (int)(patients * returnRate);
            int newPats = patients - retPats;

            // 비용 세부 Map
            Map<String, Long> mCostBreakdown = new LinkedHashMap<>();
            mCostBreakdown.put("인건비",    laborCost);
            mCostBreakdown.put("4대보험",   insuranceCost);
            mCostBreakdown.put("임대료",    rentCost);
            mCostBreakdown.put("마케팅비",  marketingCost);
            mCostBreakdown.put("감가상각비", depreciationCost);
            mCostBreakdown.put("기타관리비", otherMgmtCost);
            mCostBreakdown.put("변동비",    variableCost);
            mCostBreakdown.put("이자비용",  interest);

            // 전월 대비 증감율
            double mCashCR = 0.0, mPatsCR = 0.0, mRepCR = 0.0;
            if (!monthlyList.isEmpty()) {
                MonthlyData prev = monthlyList.get(monthlyList.size() - 1);
                long   prevCash  = prev.getCumulativeCash()  != null ? prev.getCumulativeCash()  : 0L;
                int    prevPats  = prev.getPatientsCount()   != null ? prev.getPatientsCount()   : 0;
                double prevRep   = prev.getReputationScore() != null ? prev.getReputationScore() : 0.0;
                if (prevCash > 0) mCashCR = round4((double)(cashBalance - prevCash) / prevCash);
                if (prevPats > 0) mPatsCR = round4((double)(patients - prevPats) / prevPats);
                if (prevRep  > 0) mRepCR  = round4((reputation - prevRep) / prevRep);
            }

            monthlyList.add(MonthlyData.builder()
                    .month(month)
                    .patientsCount(patients)
                    .newPatientCount(newPats)
                    .returnPatientCount(retPats)
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
                    .cashChangeRate(mCashCR)
                    .patientsChangeRate(mPatsCR)
                    .reputationChangeRate(mRepCR)
                    .reputationScore(round2(reputation))
                    .patientSatisfaction(round2(satisfaction))
                    .returnPatientRate(round3(returnRate))
                    .staffMorale(round3(staffMorale))
                    .laborCost(laborCost)
                    .insuranceCost(insuranceCost)
                    .rentCost(rentCost)
                    .marketingCost(marketingCost)
                    .depreciationCost(depreciationCost)
                    .otherMgmtCost(otherMgmtCost)
                    .costBreakdown(mCostBreakdown)
                    .activeEvents(monthEvents)
                    .appliedDecisions(Collections.emptyList())
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
    private double round2(double v) { return Math.round(v * 100)   / 100.0; }
    private double round3(double v) { return Math.round(v * 1000)  / 1000.0; }
    private double round4(double v) { return Math.round(v * 10000) / 10000.0; }
}

package com.medsim.backend.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.Map;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonthlyData {

    private Integer month;              // 몇 번째 달 (1~36)


    // ── 진료 지표 ────────────────────────────────────────────────────────────────────

    // 이번 달 전체 진료 환자 수
    private Integer patientsCount;

    // 이번 달 신규 환자 수 (환자 유형 도넛 차트 - 신규 영역)
    private Integer newPatientCount;

    // 이번 달 재진 환자 수 (환자 유형 도넛 차트 - 재진 영역)
    private Integer returnPatientCount;


    // ── 손익계산서 ───────────────────────────────────────────────────────────────────

    // 매출 (환자수 × 건당 수가)
    private Long revenue;

    // 변동비 (매출의 15% - 의약품비, 재료비 등)
    private Long variableCost;

    // 고정비 (인건비 + 임대료 + 감가상각 + 마케팅 + 기타관리비)
    private Long fixedCost;

    // 영업이익 = 매출 - 변동비 - 고정비
    private Long operatingProfit;

    // 이자비용 (원금 균등 상환, 매월 감소)
    private Long interestExpense;

    // 세금 (영업이익 > 0일 때만)
    private Long taxExpense;

    // 당기순이익 = 영업이익 - 이자 - 세금
    private Long netProfit;


    // ── 현금흐름표 ───────────────────────────────────────────────────────────────────

    // 영업 현금흐름 (실제 들어오고 나간 돈)
    private Long operatingCashFlow;

    // 투자 현금흐름 (1개월차 초기투자금 지출만)
    private Long investingCashFlow;

    // 재무 현금흐름 (대출 실행 및 원금 상환)
    private Long financingCashFlow;

    // 누적 현금잔고 (마이너스면 파산 트리거)
    private Long cumulativeCash;


    // ── 전월 대비 증감율 (KPI 카드 우측 +1.2% / -11% 뱃지용) ───────────────────────

    // 현금잔고 전월 대비 증감율 (예: +0.012 = +1.2%)
    private Double cashChangeRate;

    // 환자수 전월 대비 증감율 (예: -0.11 = -11%)
    private Double patientsChangeRate;

    // 평판 전월 대비 증감율
    private Double reputationChangeRate;


    // ── 비재무 지표 ──────────────────────────────────────────────────────────────────

    // 병원 평판 0~5.0
    private Double reputationScore;

    // 환자 만족도 0~5.0
    private Double patientSatisfaction;

    // 재진율 0~1.0
    private Double returnPatientRate;

    // 직원 사기 0~1.0 (낮으면 서비스 품질 하락)
    private Double staffMorale;


    // ── 비용 세부 내역 (자금 지출 순위 도넛 차트용) ─────────────────────────────────

    // 월별 항목별 비용 breakdown
    // 예: {"인건비": 5000000, "마케팅비": 800000, "임대료": 3000000, "의료소모품": 500000}
    private Map<String, Long> costBreakdown;


    // ── 이벤트 / 의사결정 이력 ───────────────────────────────────────────────────────

    // 이번 달 발생한 이벤트 목록 (이벤트 센터 요약 카드용)
    private List<SimulationEvent> activeEvents;

    // 이번 달 유저가 선택한 의사결정 목록 (AI 추천 액션 / 운영 이력 탭용)
    private List<Decision> appliedDecisions;
}
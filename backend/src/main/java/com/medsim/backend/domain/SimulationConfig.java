package com.medsim.backend.domain;

public final class SimulationConfig {

    private SimulationConfig() {}

    // ── 진료 기본값 (강남구/내과 기준, 추후 엑셀 파일 받은 후 수정 가능)

    // 하루 평균 진료 환자 수 (강남구/내과 리서치 기반 추정값)
    public static final int    BASE_PATIENTS_PER_DAY  = 40;

    // 환자 1명당 평균 수가 (원 단위, 내과 기준 약 4.5만원)
    public static final long   REVENUE_PER_PATIENT    = 45_000L;

    // 지역 보정 계수 (강남구 기준 1.0 / 외곽 지역은 0.7~0.8 예정)
    public static final double AREA_FACTOR            = 1.0;

    // 월 실제 진료 가능 일수 (주 5일 기준 약 22일)
    public static final int    WORKING_DAYS_PER_MONTH = 22;


    // ── 시뮬레이션 초기 상태값 (개원 첫 달 시작 시 기본값) ──────────────────────────

    // 초기 병원 평판 점수 (0~5.0 / 신규 개원이므로 중간값 3.0으로 시작)
    public static final double INITIAL_REPUTATION     = 3.0;

    // 초기 재진율 (0~1.0 / 신규 개원 기준 30% → 경영 잘 할수록 올라감)
    public static final double INITIAL_RETURN_RATE    = 0.3;

    // 초기 환자 만족도 (0~5.0 / 평판과 함께 움직이는 지표)
    public static final double INITIAL_SATISFACTION   = 3.0;

    // 초기 직원 사기 (0~1.0 / 직원 처우에 따라 매달 변동)
    public static final double INITIAL_STAFF_MORALE   = 0.8;


    // ── 재무 계산 상수 (매달 손익 계산 시 사용) ─────────────────────────────────────

    // 변동비율 (매출 대비 소모품·재료비 등 비율, 내과 기준 약 30%)
    public static final double VARIABLE_COST_RATIO    = 0.3;

    // 세율 (순이익 대비 세금 비율, 단순화된 추정값 10%)
    public static final double TAX_RATE               = 0.1;


    // ── 시뮬레이션 설정 ──────────────────────────────────────────────────────────────

    // 총 시뮬레이션 기간 (36개월 = 3년, 서비스 핵심 스펙)
    public static final int    TOTAL_MONTHS           = 36;


    // ── 경영 등급 산정 기준 (36개월 완료 후 최종 등급 결정에 사용) ─────────────────

    // S등급 조건: 흑자 + 평판 4.0 이상 + 재진율 0.5 이상
    public static final double GRADE_S_REPUTATION     = 4.0;
    public static final double GRADE_S_RETURN_RATE    = 0.5;

    // A등급 조건: 흑자 + 평판 3.5 이상
    public static final double GRADE_A_REPUTATION     = 3.5;
}
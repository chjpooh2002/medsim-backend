package com.medsim.backend.service.pricing;

/**
 * 내과 진료 단가표 (단위: 원)
 *
 * 진찰료(초진/재진)는 건강보험 수가 기준 실제값.
 * 검사·처치 단가는 MVP 가정값이며 실제 수가와 상이할 수 있음.
 * NK세포·유전자검사는 MVP 미포함.
 */
public final class MedicalPricing {

    private MedicalPricing() {}

    // ── 진찰료 (건강보험 수가 기준) ──────────────────────────────────────
    /** 초진 진찰료 */
    public static final int CONSULTATION_NEW    = 18_840;
    /** 재진 진찰료 */
    public static final int CONSULTATION_RETURN = 13_370;

    // ── 검사·처치 단가 (MVP 가정값) ──────────────────────────────────────
    /** 위내시경 (가정값) */
    public static final int GASTROSCOPY  = 120_000;
    /** 대장내시경 (가정값) */
    public static final int COLONOSCOPY  = 215_000;
    /**
     * 위·대장 동시 내시경 (가정값).
     * 위/대장 단독 항목과 중복 집계 금지.
     */
    public static final int ENDO_BOTH    = 265_000;
    /** 초음파 (가정값) */
    public static final int ULTRASOUND   = 105_000;
    /** 수액 (가정값) */
    public static final int IV_FLUID     = 130_000;
    /**
     * 기타진료비 — 처방·주사·기본검사 묶음 (가정값).
     * 건강보험 급여 외 처치 및 비급여 항목 평균 추정치.
     */
    public static final int OTHER_TREATMENT = 25_000;
}

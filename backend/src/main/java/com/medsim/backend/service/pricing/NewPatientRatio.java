package com.medsim.backend.service.pricing;

/**
 * 월차(t)별 신환 비율 계산.
 *
 * <pre>
 * NewRate(t) = 0.15 + 0.85 / (1 + exp(0.22 × (t − 12)))
 * </pre>
 *
 * t=1 고정 1.00, 하한 0.15.
 */
public final class NewPatientRatio {

    private NewPatientRatio() {}

    private static final double FLOOR = 0.15;
    private static final double SCALE = 0.85;
    private static final double K     = 0.22;
    private static final int    MID   = 12;

    /**
     * @param month 시뮬레이션 월차 (1~36)
     * @return 신환 비율 [0.15, 1.00]
     */
    public static double of(int month) {
        if (month <= 1) return 1.0;
        double rate = FLOOR + SCALE / (1.0 + Math.exp(K * (month - MID)));
        return Math.max(FLOOR, rate);
    }
}

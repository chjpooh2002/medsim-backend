package com.medsim.backend.domain;

public final class SimulationConfig {

    private SimulationConfig() {}

    // 강남구/내과 프리셋 기본값
    public static final int    BASE_PATIENTS_PER_DAY = 40;
    public static final double REVENUE_PER_PATIENT   = 4.5;  // 만원
    public static final double AREA_FACTOR           = 1.0;
    public static final double INITIAL_REPUTATION    = 3.0;  // 0~5.0
    public static final double INITIAL_RETURN_RATE   = 0.3;  // 0~1.0
}

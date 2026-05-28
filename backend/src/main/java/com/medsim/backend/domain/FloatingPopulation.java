package com.medsim.backend.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class FloatingPopulation {

    private final String dong;
    private final String district;
    /** 일평균 유동인구 (명/일) — 분기합계 ÷ 90일 기준 */
    private final int avgDailyPopulation;
    /** 동 직접 매칭 실패 → 구 평균으로 fallback된 경우 true */
    private final boolean fallback;
}

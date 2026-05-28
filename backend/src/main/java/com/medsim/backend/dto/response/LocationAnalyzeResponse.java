package com.medsim.backend.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class LocationAnalyzeResponse {

    private final String sido;
    private final String sigungu;
    private final String dong;
    private final Double lat;
    private final Double lng;
    private final Integer clinicSizePyeong;
    private final String radius;

    /** 평당 임대료 (원/월) */
    private final int pricePerPyeong;
    /** 평당 보증금 (원) */
    private final int depositPerPyeong;
    /** 월 임대료 = pricePerPyeong × clinicSizePyeong (clinicSizePyeong 미입력 시 null) */
    private final Long monthlyRent;
    /** 보증금 = depositPerPyeong × clinicSizePyeong (clinicSizePyeong 미입력 시 null) */
    private final Long deposit;

    /** 일평균 유동인구 (명/일) */
    private final Integer floatingPopulation;
    /** 경쟁 병원 수 (-1 = 미조회) */
    private final Integer competitorCount;

    /** 입지 수요 점수 (0~100) */
    private final Double locationDemandScore;
    /** 입지 리스크 레벨 ("낮음"/"보통"/"높음") */
    private final String locationRiskLevel;
    /** 주요 연령대 (nullable — 추후 데이터 연동 예정) */
    private final String mainAgeGroup;

    /** 동 직접 매칭 실패 → 구 평균으로 응답한 경우 true */
    private final boolean fallback;
}

package com.medsim.backend.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class RentData {

    private final String dong;
    private final String district;
    /** 평당 임대료 (원/월) */
    private final int pricePerPyeong;
    /** 평당 보증금 (원) */
    private final int depositPerPyeong;
    /** 동 직접 매칭이 아닌 구 평균으로 fallback된 경우 true */
    private final boolean fallback;
}

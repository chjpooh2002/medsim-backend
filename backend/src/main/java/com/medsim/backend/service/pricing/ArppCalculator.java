package com.medsim.backend.service.pricing;

/**
 * 장비 수준별 ARPP(환자 1인당 평균 진료비) 계산기.
 * 내부 계산 단위: 원.  호출 측에서 만원 변환.
 *
 * <pre>
 * ARPP_일반 = 진찰료 + 기타진료비
 * ARPP_검진 = 진찰료 + 레벨별 검진코어
 * ARPP_특화 = 진찰료 + 레벨별 특화코어
 * 혼합 ARPP = generalMix×ARPP_일반 + checkupMix×ARPP_검진 + specialMix×ARPP_특화
 * </pre>
 *
 * 초진·재진은 진찰료 단가만 교체해 산출한다.
 * 초진 프리미엄 배수를 별도로 곱하지 않는다 (진찰료 차이로 이미 반영됨).
 */
public final class ArppCalculator {

    private ArppCalculator() {}

    /**
     * 초진 혼합 ARPP (원).
     */
    public static double newPatientArpp(EquipmentLevel level) {
        return mixedArpp(level, MedicalPricing.CONSULTATION_NEW);
    }

    /**
     * 재진 혼합 ARPP (원).
     */
    public static double returnPatientArpp(EquipmentLevel level) {
        return mixedArpp(level, MedicalPricing.CONSULTATION_RETURN);
    }

    private static double mixedArpp(EquipmentLevel level, int consultationFee) {
        double general = consultationFee + MedicalPricing.OTHER_TREATMENT;
        double checkup = consultationFee + level.checkupCore;
        double special = consultationFee + level.specialCore;
        return level.generalMix * general
             + level.checkupMix * checkup
             + level.specialMix * special;
    }
}

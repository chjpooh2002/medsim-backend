package com.medsim.backend.service.pricing;

/**
 * 월별 매출 계산기.
 *
 * <pre>
 * Revenue_t = 환자수 × NewRate   × ARPP_초진
 *           + 환자수 × (1-NewRate) × ARPP_재진
 * </pre>
 *
 * 반환 단위: 만원 (내부 단가는 원으로 계산 후 변환).
 */
public final class RevenueCalculator {

    private RevenueCalculator() {}

    private static final double WON_TO_MANWON = 10_000.0;

    /**
     * 버킷별 매출 breakdown 포함 결과.
     *
     * @param totalRevenueManwon        총 매출 (만원)
     * @param newPatientRevenueManwon   초진 환자 매출 (만원)
     * @param returnPatientRevenueManwon 재진 환자 매출 (만원)
     * @param newPatientCount           초진 환자 수
     * @param returnPatientCount        재진 환자 수
     * @param newRate                   신환 비율 [0.15, 1.00]
     */
    public record Result(
        long   totalRevenueManwon,
        long   newPatientRevenueManwon,
        long   returnPatientRevenueManwon,
        int    newPatientCount,
        int    returnPatientCount,
        double newRate
    ) {}

    /**
     * @param month    시뮬레이션 월차 (1~36)
     * @param patients 해당 월 총 환자 수
     * @param level    장비 수준
     * @return 매출 결과 (만원)
     */
    public static Result calculate(int month, int patients, EquipmentLevel level) {
        double newRate    = NewPatientRatio.of(month);
        int    newPats    = (int) Math.round(patients * newRate);
        int    returnPats = patients - newPats;

        long newRev    = Math.round(newPats    * ArppCalculator.newPatientArpp(level)    / WON_TO_MANWON);
        long returnRev = Math.round(returnPats * ArppCalculator.returnPatientArpp(level) / WON_TO_MANWON);

        return new Result(newRev + returnRev, newRev, returnRev, newPats, returnPats, newRate);
    }
}

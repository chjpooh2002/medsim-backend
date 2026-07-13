package com.medsim.backend.service.pricing;

/**
 * 장비 수준별 진료 Mix 비율 및 버킷별 코어 단가 (단위: 원).
 *
 * <p><b>검진 코어 단가(checkupCore) 산출 근거:</b>
 * <ul>
 *   <li>하  : 내시경·초음파 미보유 → X-ray·혈액검사 수준 (가정값 35,000)</li>
 *   <li>중  : 초음파 80% + 기본검사 20%
 *             = 0.8 × 105,000 + 0.2 × 35,000 = 91,000</li>
 *   <li>상  : 위30% + 대장15% + 위·대장동시25% + 초음파30%
 *             = 36,000 + 32,250 + 66,250 + 31,500 = 166,000</li>
 *   <li>최상: 위25% + 대장10% + 위·대장동시40% + 초음파25%
 *             = 30,000 + 21,500 + 106,000 + 26,250 = 183,750</li>
 * </ul>
 * 검진 코어는 장비 수준에 종속되므로, 내시경 미보유 병원(하·중)은
 * 내시경 단가를 검진 코어로 사용할 수 없다.
 */
public enum EquipmentLevel {

    //             generalMix   checkupMix  specialMix  checkupCore  specialCore
    /** 하: 내시경·초음파 미보유 */
    LOW    (        0.80,        0.15,       0.05,        35_000,      60_000),
    /** 중: 초음파 보유, 내시경 미보유 */
    MID    (        0.60,        0.30,       0.10,        91_000,     100_000),
    /** 상: 내시경·초음파 모두 보유 */
    HIGH   (        0.475,       0.375,      0.15,       166_000,     130_000),
    /** 최상: 고급 내시경 및 복합 검진 중심 */
    PREMIUM(        0.40,        0.40,       0.20,       183_750,     130_000);

    /** 일반진료 비율 */
    public final double generalMix;
    /** 검진 비율 */
    public final double checkupMix;
    /** 특화진료 비율 */
    public final double specialMix;
    /** 장비 수준에 종속된 검진 코어 단가 (원) */
    public final int checkupCore;
    /** 특화진료 코어 단가 (원) */
    public final int specialCore;

    EquipmentLevel(double generalMix, double checkupMix, double specialMix,
                   int checkupCore, int specialCore) {
        this.generalMix  = generalMix;
        this.checkupMix  = checkupMix;
        this.specialMix  = specialMix;
        this.checkupCore = checkupCore;
        this.specialCore = specialCore;
    }
}

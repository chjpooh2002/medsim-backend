package com.medsim.backend.service.pricing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RevenueModelTest {

    // ── ARPP 범위 검증 ────────────────────────────────────────────────────

    @Test
    void arpp_하레벨() {
        double newArpp    = ArppCalculator.newPatientArpp(EquipmentLevel.LOW);
        double returnArpp = ArppCalculator.returnPatientArpp(EquipmentLevel.LOW);

        assertEquals(47_090, newArpp,    0.5, "하 초진 ARPP");
        assertEquals(41_620, returnArpp, 0.5, "하 재진 ARPP");
        assertTrue(newArpp    >= 35_000 && newArpp    <= 55_000, "하 초진 범위 이탈: " + newArpp);
        assertTrue(returnArpp >= 35_000 && returnArpp <= 55_000, "하 재진 범위 이탈: " + returnArpp);
    }

    @Test
    void arpp_중레벨() {
        double newArpp    = ArppCalculator.newPatientArpp(EquipmentLevel.MID);
        double returnArpp = ArppCalculator.returnPatientArpp(EquipmentLevel.MID);

        assertEquals(71_140, newArpp,    0.5, "중 초진 ARPP");
        assertEquals(65_670, returnArpp, 0.5, "중 재진 ARPP");
        assertTrue(newArpp    >= 65_000 && newArpp    <= 85_000, "중 초진 범위 이탈: " + newArpp);
        assertTrue(returnArpp >= 65_000 && returnArpp <= 85_000, "중 재진 범위 이탈: " + returnArpp);
    }

    @Test
    void arpp_상레벨() {
        double newArpp    = ArppCalculator.newPatientArpp(EquipmentLevel.HIGH);
        double returnArpp = ArppCalculator.returnPatientArpp(EquipmentLevel.HIGH);

        assertEquals(112_465, newArpp,    0.5, "상 초진 ARPP");
        assertEquals(106_995, returnArpp, 0.5, "상 재진 ARPP");
        assertTrue(newArpp    >= 90_000  && newArpp    <= 120_000, "상 초진 범위 이탈: " + newArpp);
        assertTrue(returnArpp >= 90_000  && returnArpp <= 120_000, "상 재진 범위 이탈: " + returnArpp);
    }

    @Test
    void arpp_최상레벨() {
        double newArpp    = ArppCalculator.newPatientArpp(EquipmentLevel.PREMIUM);
        double returnArpp = ArppCalculator.returnPatientArpp(EquipmentLevel.PREMIUM);

        assertEquals(128_340, newArpp,    0.5, "최상 초진 ARPP");
        assertEquals(122_870, returnArpp, 0.5, "최상 재진 ARPP");
        assertTrue(newArpp    >= 110_000 && newArpp    <= 150_000, "최상 초진 범위 이탈: " + newArpp);
        assertTrue(returnArpp >= 110_000 && returnArpp <= 150_000, "최상 재진 범위 이탈: " + returnArpp);
    }

    // ── NewPatientRatio 검증 ──────────────────────────────────────────────

    @Test
    void newRate_t1_고정값() {
        assertEquals(1.00, NewPatientRatio.of(1), 0.001, "t=1 고정값");
    }

    @Test
    void newRate_t36_하한근사() {
        double rate = NewPatientRatio.of(36);
        // 0.15 + 0.85 / (1 + exp(0.22×24)) ≈ 0.154
        assertTrue(rate >= 0.15 && rate < 0.20, "t=36 신환비율: " + rate);
    }
}

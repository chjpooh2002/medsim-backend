package com.medsim.backend.controller;

import com.medsim.backend.domain.FloatingPopulation;
import com.medsim.backend.domain.RentData;
import com.medsim.backend.dto.response.LocationAnalyzeResponse;
import com.medsim.backend.service.FloatingPopulationService;
import com.medsim.backend.service.RentDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/location")
@RequiredArgsConstructor
public class LocationController {

    private final RentDataService rentDataService;
    private final FloatingPopulationService floatingPopulationService;

    /**
     * 동 선택 시 임대료·보증금·유동인구·입지점수를 반환한다.
     * competitorCount가 -1(미조회)이면 수요점수 계산에 평균값 5를 대입한다.
     */
    @GetMapping("/analyze")
    public ResponseEntity<LocationAnalyzeResponse> analyze(
            @RequestParam String dong,
            @RequestParam(required = false, defaultValue = "서울특별시") String sido,
            @RequestParam(required = false) String sigungu,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng,
            @RequestParam(required = false, defaultValue = "0") int clinicSizePyeong,
            @RequestParam(required = false) String radius,
            @RequestParam(required = false, defaultValue = "-1") int competitorCount) {

        return rentDataService.resolveRentData(dong, sigungu)
                .map(data -> buildResponse(data, sido, dong, lat, lng,
                        clinicSizePyeong, radius, competitorCount))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    private LocationAnalyzeResponse buildResponse(RentData data, String sido, String dong,
                                                   Double lat, Double lng,
                                                   int clinicSizePyeong, String radius,
                                                   int competitorCount) {
        Long monthlyRent = clinicSizePyeong > 0
                ? rentDataService.calculateMonthlyRent(data, clinicSizePyeong) : null;
        Long deposit = clinicSizePyeong > 0
                ? rentDataService.calculateDeposit(data, clinicSizePyeong) : null;

        Integer floatingPopulation = floatingPopulationService
                .resolvePopulation(dong, data.getDistrict())
                .map(FloatingPopulation::getAvgDailyPopulation)
                .orElse(null);

        double demandScore = calcDemandScore(floatingPopulation, competitorCount, data.getDistrict());
        String riskLevel   = calcRiskLevel(data.getPricePerPyeong(), competitorCount);

        return LocationAnalyzeResponse.builder()
                .sido(sido)
                .sigungu(data.getDistrict())
                .dong(data.getDong())
                .lat(lat)
                .lng(lng)
                .clinicSizePyeong(clinicSizePyeong > 0 ? clinicSizePyeong : null)
                .radius(radius)
                .pricePerPyeong(data.getPricePerPyeong())
                .depositPerPyeong(data.getDepositPerPyeong())
                .monthlyRent(monthlyRent)
                .deposit(deposit)
                .floatingPopulation(floatingPopulation)
                .competitorCount(competitorCount)
                .locationDemandScore(demandScore)
                .locationRiskLevel(riskLevel)
                .mainAgeGroup(null)
                .fallback(data.isFallback())
                .build();
    }

    /**
     * score = (floatingPopulation / 1000 * 0.4)
     *       + ((10 - effectiveCompetitors) * 3 * 0.4)
     *       + (regionWeight(0~20) * 0.2)
     * clamp to [0, 100]
     */
    private double calcDemandScore(Integer floatingPopulation, int competitorCount, String district) {
        double popScore  = (floatingPopulation != null ? floatingPopulation / 1000.0 : 0) * 0.4;
        int effective    = (competitorCount == -1) ? 5 : competitorCount;
        double compScore = (10 - effective) * 3 * 0.4;
        double regScore  = getRegionWeight(district) * 0.2;
        double raw       = popScore + compScore + regScore;
        return Math.round(Math.max(0, Math.min(100, raw)) * 10.0) / 10.0;
    }

    /**
     * 평당 임대료 또는 경쟁 수 기준으로 리스크 레벨 결정.
     * competitorCount == -1(미조회)이면 임대료만으로 판단한다.
     */
    private String calcRiskLevel(int pricePerPyeong, int competitorCount) {
        if (pricePerPyeong > 100000 || (competitorCount != -1 && competitorCount >= 5)) return "높음";
        if (pricePerPyeong > 75000  || (competitorCount != -1 && competitorCount >= 3)) return "보통";
        return "낮음";
    }

    /** 구별 입지 가중치 (0~20) */
    private int getRegionWeight(String district) {
        if (district == null) return 10;
        return switch (district) {
            case "강남구" -> 20;
            case "서초구" -> 18;
            case "용산구" -> 17;
            case "마포구" -> 16;
            case "종로구", "송파구" -> 15;
            case "성동구" -> 14;
            default -> 10;
        };
    }
}

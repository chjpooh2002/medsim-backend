package com.medsim.backend.service;

import com.medsim.backend.domain.RentData;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class RentDataService {

    private static final Map<String, RentData> DONG_MAP = new HashMap<>();

    /**
     * 구별 fallback 임대료 (pricePerPyeong 원/평, depositPerPyeong 원/평).
     * 동 단위 데이터가 없을 때 사용. 강남구 기준: 120000 × 50평 = 6,000,000원/월
     */
    private static final Map<String, int[]> DISTRICT_FALLBACK = Map.ofEntries(
        Map.entry("강남구", new int[]{120000, 2900000}),
        Map.entry("서초구", new int[]{ 92500, 2250000}),
        Map.entry("송파구", new int[]{ 87500, 2150000}),
        Map.entry("마포구", new int[]{ 84333, 2033000}),
        Map.entry("종로구", new int[]{ 82500, 1950000}),
        Map.entry("용산구", new int[]{107500, 2650000}),
        Map.entry("성동구", new int[]{ 86500, 2075000})
    );

    static {
        List<RentData> data = List.of(
            new RentData("역삼1동",    "강남구", 120000, 3000000, false),
            new RentData("삼성1동",    "강남구", 110000, 2800000, false),
            new RentData("논현1동",    "강남구", 105000, 2600000, false),
            new RentData("청담동",     "강남구", 130000, 3200000, false),
            new RentData("서초1동",    "서초구", 100000, 2500000, false),
            new RentData("방배1동",    "서초구",  85000, 2000000, false),
            new RentData("잠실1동",    "송파구",  95000, 2400000, false),
            new RentData("문정1동",    "송파구",  80000, 1900000, false),
            new RentData("서교동",     "마포구",  90000, 2200000, false),
            new RentData("합정동",     "마포구",  88000, 2100000, false),
            new RentData("망원1동",    "마포구",  75000, 1800000, false),
            new RentData("혜화동",     "종로구",  85000, 2000000, false),
            new RentData("사직동",     "종로구",  80000, 1900000, false),
            new RentData("이태원1동",  "용산구", 100000, 2500000, false),
            new RentData("한남동",     "용산구", 115000, 2800000, false),
            new RentData("성수1가1동", "성동구",  95000, 2300000, false),
            new RentData("왕십리1동",  "성동구",  78000, 1850000, false)
        );

        for (RentData rd : data) {
            DONG_MAP.put(rd.getDong(), rd);
        }
    }

    /** 동 이름으로 정확히 조회 */
    public Optional<RentData> findByDong(String dong) {
        return Optional.ofNullable(DONG_MAP.get(dong));
    }

    /** 구 이름으로 fallback 조회 (DISTRICT_FALLBACK 하드코딩 기준) */
    public Optional<RentData> findByDistrict(String district) {
        int[] prices = DISTRICT_FALLBACK.get(district);
        if (prices == null) return Optional.empty();
        return Optional.of(new RentData(district + " 평균", district, prices[0], prices[1], true));
    }

    /**
     * 동 이름으로 조회하고, 없으면 구 평균으로 fallback.
     * district가 null이면 fallback 없이 빈 Optional 반환.
     */
    public Optional<RentData> resolveRentData(String dong, String district) {
        return findByDong(dong)
                .or(() -> district != null ? findByDistrict(district) : Optional.empty());
    }

    public long calculateMonthlyRent(RentData rentData, int clinicSizePyeong) {
        return (long) rentData.getPricePerPyeong() * clinicSizePyeong;
    }

    public long calculateDeposit(RentData rentData, int clinicSizePyeong) {
        return (long) rentData.getDepositPerPyeong() * clinicSizePyeong;
    }
}

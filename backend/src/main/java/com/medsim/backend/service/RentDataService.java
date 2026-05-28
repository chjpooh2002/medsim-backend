package com.medsim.backend.service;

import com.medsim.backend.domain.RentData;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class RentDataService {

    private static final Map<String, RentData> DONG_MAP = new HashMap<>();
    private static final Map<String, List<RentData>> DISTRICT_MAP = new HashMap<>();

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
            DISTRICT_MAP.computeIfAbsent(rd.getDistrict(), k -> new ArrayList<>()).add(rd);
        }
    }

    /** 동 이름으로 정확히 조회 */
    public Optional<RentData> findByDong(String dong) {
        return Optional.ofNullable(DONG_MAP.get(dong));
    }

    /** 구 이름으로 평균값 조회 */
    public Optional<RentData> findByDistrict(String district) {
        List<RentData> list = DISTRICT_MAP.get(district);
        if (list == null || list.isEmpty()) return Optional.empty();
        int avgPrice   = (int) list.stream().mapToInt(RentData::getPricePerPyeong).average().orElse(0);
        int avgDeposit = (int) list.stream().mapToInt(RentData::getDepositPerPyeong).average().orElse(0);
        return Optional.of(new RentData(district + " 평균", district, avgPrice, avgDeposit, true));
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

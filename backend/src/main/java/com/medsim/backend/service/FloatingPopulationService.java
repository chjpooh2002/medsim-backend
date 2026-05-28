package com.medsim.backend.service;

import com.medsim.backend.domain.FloatingPopulation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 서울특별시 상권분석서비스 길단위인구_행정동 기준 (2025년 4분기, 분기합계 ÷ 90일)
 * 잠실1동 → 잠실본동, 왕십리1동 → 왕십리2동 으로 데이터셋 대체
 * 주요 연령대(mainAgeGroup)도 동일 출처 2025년 4분기 데이터 기반
 */
@Slf4j
@Service
public class FloatingPopulationService {

    private static final Map<String, FloatingPopulation> DONG_MAP = new HashMap<>();
    private static final Map<String, List<FloatingPopulation>> DISTRICT_MAP = new HashMap<>();
    /** 동별 주요 연령대 — 없는 동은 null 반환 */
    private static final Map<String, String> AGE_GROUP_MAP = new HashMap<>();

    static {
        List<FloatingPopulation> data = List.of(
            new FloatingPopulation("역삼1동",    "강남구", 220879, false),
            new FloatingPopulation("삼성1동",    "강남구",  54705, false),
            new FloatingPopulation("논현1동",    "강남구", 102193, false),
            new FloatingPopulation("청담동",     "강남구", 101609, false),
            new FloatingPopulation("서초1동",    "서초구",  82792, false),
            new FloatingPopulation("방배1동",    "서초구",  48700, false),
            new FloatingPopulation("잠실본동",   "송파구",  89989, false),
            new FloatingPopulation("문정1동",    "송파구",  46290, false),
            new FloatingPopulation("서교동",     "마포구", 191284, false),
            new FloatingPopulation("합정동",     "마포구",  48284, false),
            new FloatingPopulation("망원1동",    "마포구",  74177, false),
            new FloatingPopulation("혜화동",     "종로구", 102780, false),
            new FloatingPopulation("사직동",     "종로구",  43447, false),
            new FloatingPopulation("이태원1동",  "용산구",  27516, false),
            new FloatingPopulation("한남동",     "용산구",  48664, false),
            new FloatingPopulation("성수1가1동", "성동구",  33025, false),
            new FloatingPopulation("왕십리2동",  "성동구",  51496, false)
        );

        for (FloatingPopulation fp : data) {
            DONG_MAP.put(fp.getDong(), fp);
            DISTRICT_MAP.computeIfAbsent(fp.getDistrict(), k -> new ArrayList<>()).add(fp);
        }

        AGE_GROUP_MAP.put("역삼1동",    "30대 위주");
        AGE_GROUP_MAP.put("삼성1동",    "40대 위주");
        AGE_GROUP_MAP.put("논현1동",    "30대 위주");
        AGE_GROUP_MAP.put("청담동",     "40대 위주");
        AGE_GROUP_MAP.put("서초1동",    "40대 위주");
        AGE_GROUP_MAP.put("방배1동",    "60대이상 위주");
        AGE_GROUP_MAP.put("문정1동",    "60대이상 위주");
        AGE_GROUP_MAP.put("서교동",     "20대 위주");
        AGE_GROUP_MAP.put("합정동",     "30대 위주");
        AGE_GROUP_MAP.put("망원1동",    "30대 위주");
        AGE_GROUP_MAP.put("혜화동",     "20대 위주");
        AGE_GROUP_MAP.put("사직동",     "40대 위주");
        AGE_GROUP_MAP.put("이태원1동",  "20대 위주");
        AGE_GROUP_MAP.put("한남동",     "30대 위주");
        AGE_GROUP_MAP.put("성수1가1동", "30대 위주");
        AGE_GROUP_MAP.put("잠실본동",   "30대 위주");
        AGE_GROUP_MAP.put("왕십리2동",  "60대이상 위주");
    }

    /** 동 이름으로 정확히 조회 */
    public Optional<FloatingPopulation> findByDong(String dong) {
        return Optional.ofNullable(DONG_MAP.get(dong));
    }

    /** 구 이름으로 평균값 조회 */
    public Optional<FloatingPopulation> findByDistrict(String district) {
        List<FloatingPopulation> list = DISTRICT_MAP.get(district);
        if (list == null || list.isEmpty()) return Optional.empty();
        int avg = (int) list.stream().mapToInt(FloatingPopulation::getAvgDailyPopulation).average().orElse(0);
        return Optional.of(new FloatingPopulation(district + " 평균", district, avg, true));
    }

    /**
     * 동 이름으로 조회하고, 없으면 구 평균으로 fallback.
     * district가 null이면 fallback 없이 빈 Optional 반환.
     */
    public Optional<FloatingPopulation> resolvePopulation(String dong, String district) {
        return findByDong(dong)
                .or(() -> district != null ? findByDistrict(district) : Optional.empty());
    }

    /**
     * 동 이름으로 주요 연령대 조회.
     * 데이터가 없는 동이면 null 반환.
     * 반환값 예시: "30대 위주", "60대이상 위주"
     */
    public String getMainAgeGroup(String dong) {
        String result = AGE_GROUP_MAP.getOrDefault(dong, "30-40대 위주");
        log.debug("[AgeGroup] dong='{}' → '{}'", dong, result);
        return result;
    }
}

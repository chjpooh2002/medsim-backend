package com.medsim.backend.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.util.Map;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Decision {

    public enum DecisionType {
        MARKETING_UP,    // 마케팅 강화
        STAFF_HIRE,      // 직원 채용
        COST_CUT,        // 비용 절감
        SERVICE_IMPROVE  // 서비스 개선
    }

    private String       decisionId;
    private int          month;
    private DecisionType decisionType;
    private String       description;

    /**
     * 의사결정 실행 시 적용할 효과.
     * 키: "revenue"(매출 배율 델타), "reputation", "satisfaction",
     *     "returnRate", "staffMorale", "fixedCost"(만원, 고정비 증감)
     */
    private Map<String, Double> effectMap;
}

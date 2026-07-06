package com.medsim.backend.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SimulationEvent {

    public enum EventType {
        COMPETITOR_OPEN,  // 경쟁 병원 개원
        PATIENT_SURGE,    // 환자 급증
        STAFF_QUIT,       // 직원 이탈
        EQUIPMENT_BREAK,  // 장비 고장
        REVIEW_VIRAL      // 바이럴 리뷰
    }

    private String    eventId;
    private int       triggerMonth;
    private EventType eventType;
    private String    description;

    /**
     * 이벤트 발생 시 적용할 수치 변화.
     * 키: "revenue"(매출 배율 델타), "reputation", "satisfaction",
     *     "returnRate", "staffMorale", "extraCost"(만원, 당월 고정비 추가)
     */
    private Map<String, Double> impactMap;
}

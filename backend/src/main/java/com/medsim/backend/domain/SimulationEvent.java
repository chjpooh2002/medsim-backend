package com.medsim.backend.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SimulationEvent {

    public enum EventType {
        COMPETITOR_OPEN,    // 경쟁 병원 개원
        RENT_INCREASE,      // 임대료 상승
        TRAFFIC_DECREASE,   // 상권 변화
        STAFF_QUIT,         // 직원 이탈
        MEDICAL_ACCIDENT,   // 의료 사고
        EQUIPMENT_BREAK,    // 장비 고장
        REVIEW_DROP,        // 리뷰 하락
        REVIEW_VIRAL,       // 바이럴 리뷰 (긍정)
        COMPLAINT,          // 민원 증가
        LOCAL_REFERRAL,     // 지역 추천 증가
        CHECKUP_CONTRACT,   // 검진 계약 성사
        FLU_SEASON,         // 독감 시즌
        PATIENT_SURGE       // 환자 급증 (기존 호환)
    }

    public enum EventCategory {
        MARKET,     // 시장
        OPERATION,  // 운영
        PATIENT,    // 환자
        GROWTH      // 성장
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EventResponseOption {
        private String optionId;
        private String label;
        private String description;
        /**
         * 선택 시 적용할 추가 효과.
         * keys: revenue(배율 델타), extraCost(만원), reputation, satisfaction, returnRate, staffMorale
         */
        private Map<String, Double> effectMap;
    }

    private String        eventId;
    private int           triggerMonth;
    private EventType     eventType;
    private EventCategory category;
    private String        description;

    /** 이벤트 기본 효과 */
    private Map<String, Double> impactMap;

    /** 대응 옵션 4개 */
    private List<EventResponseOption> responseOptions;
}

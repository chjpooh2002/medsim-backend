package com.medsim.backend.domain;

import lombok.Getter;
import lombok.Setter;
import java.util.List;

@Getter
@Setter
public class SimulationRequest {

    private String specialty;        // 과목 (예: "내과")
    private String location;         // 상권 (예: "서초강남 B급")
    private int area;                // 면적 (평)

    private List<Staff> staffList;   // 인력 구조

    private long loan;               // 대출금 (원)
    private int loanPeriod;          // 상환기간 (개월)
    private long totalInvestment;    // 총 투자금 (원)

    private List<Marketing> marketingList; // 마케팅 구조


    @Getter
    @Setter
    public static class Staff {
        private String role;         // 직종 (의사/간호사/간호조무사/원무행정)
        private int count;           // 인원수
    }


    @Getter
    @Setter
    public static class Marketing {
        private String channel;      // 채널 (온드/바이럴/배너광고/오프라인/검색광고)
        private String item;         // 항목 (예: "블로그 운영")
        private String type;         // 유형 (월/건당/1회성)
        private int quantity;        // 월 횟수/건 (월/건당인 경우)
        private boolean selected;    // 선택 여부
    }
}

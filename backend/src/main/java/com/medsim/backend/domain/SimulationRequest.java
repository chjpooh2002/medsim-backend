package com.medsim.backend.domain;

import lombok.Getter;
import lombok.Setter;
import java.util.List;

@Getter
@Setter
public class SimulationRequest {

    // ① 진료과목
    private String deptCategory;   // 카테고리 (예: "내과")
    private String deptDetail;     // 주요 진료 세부 선택

    // 예상 상권
    private String regionSiGun;    // 시/군/구 텍스트
    private String regionDetail;   // 상세주소
    private Double lat;            // 위도 (향후 상권분석용)
    private Double lng;            // 경도 (향후 상권분석용)

    // ② 자금 및 투자 계획
    private Long initialInvestment; // 초기 투자금 (만원)
    private Long loanAmount;        // 대출금액 (만원)
    private Double loanRate;        // 연이율 (%)
    private Integer loanMonths;     // 상환기간 (개월)

    // ③ 월 운영비 설정
    private Integer rentArea;       // 평수
    private Long monthlyRent;       // 월 임대료 (만원)
    private Long monthlyMarketing;  // 월 마케팅 비용 (만원)

    // 인력 및 인건비 (직접 입력)
    private List<Staff> staffList;

    @Getter
    @Setter
    public static class Staff {
        private String role;        // 의사/간호사/간호조무사/원무행정
        private Integer count;      // 인원수
        private Long salary;        // 월 급여 (만원) — 직접 입력
    }
}

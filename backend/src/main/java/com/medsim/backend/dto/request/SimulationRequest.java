package com.medsim.backend.dto.request;

import lombok.Getter;
import lombok.Setter;
import java.util.List;

@Getter
@Setter
public class SimulationRequest {

    // 진료과목
    private String deptCategory;    // 카테고리 (예: "피부과")
    private String deptDetail;      // 세부 진료 항목

    // 상권 정보
    private String regionSiGun;     // 시/군/구 텍스트
    private String regionDetail;    // 상세주소
    private Double lat;             // 위도 (향후 상권분석 엔진용)
    private Double lng;             // 경도 (향후 상권분석 엔진용)

    // 자금 계획
    private Long    initialInvestment;  // 초기 투자금 (만원)
    private Long    loanAmount;         // 대출금액 (만원)
    private Double  loanRate;           // 연이율 (%)
    private Integer loanMonths;         // 상환기간 (개월)

    // 월 운영비
    private Integer rentArea;           // 평수
    private Long    monthlyRent;        // 월 임대료 (만원)
    private Long    monthlyMarketing;   // 월 마케팅 비용 (만원)

    // 인력 구성 (직종별 인원수 + 급여 입력)
    private List<StaffRequest> staffList;
}

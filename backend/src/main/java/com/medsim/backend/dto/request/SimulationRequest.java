package com.medsim.backend.dto.request;

import lombok.Getter;
import lombok.Setter;
import java.util.List;

@Getter
@Setter
public class SimulationRequest { // 유저가 "개원 시작하기" 버튼 누르고 입력하는 초기값들을 담는 그릇, InitialSetup 타입 정의

    // 진료과목
    private String deptCategory;    // 카테고리 (예: "피부과")
    private String deptDetail;      // 세부 진료 항목

    // 상권 정보
    private String regionSiGun;     // 시/군/구 텍스트
    private String regionDetail;    // 상세주소
    private Double lat;             // 위도 (향후 상권분석 엔진용)
    private Double lng;             // 경도 (향후 상권분석 엔진용)

    // 자금 계획
    private Long    initialInvestment;  // **초기 투자금 (만원) --> 단위 만원으로 할지 원으로 할지? (초기 투자금 입력에서)**
    private Long    loanAmount;         // 대출금액 (만원)
    private Double  loanRate;           // 연이율 (%)
    private Integer loanMonths;         // 상환기간 (개월)

    // 월 운영비
    private Integer rentArea;           // 평수
    private Long    monthlyRent;        // 월 임대료 (만원)
    private Long    monthlyMarketing;   // 월 마케팅 비용 (만원)

    // 인력 구성 (직종별 인원수 + 급여 입력)
    private List<StaffRequest> staffList;   //  **만약 staffList가 아예 없으면 어떻게 할지**

    // START-2: 페르소나 + 경력사항
    private String  personaType;          // 경영 성향 ("안정형" | "균형형" | "공격형")
    private String  graduateSchool;       // 졸업 대학 (예: "가톨릭대학교 의과대학")
    private String  specialty;            // 최종 학과/전공 (예: "내과")
    private String  subspecialty;         // 세부 분과 (예: "소화기 내과")
    private Integer careerYears;          // 총 경력 기간 (년)

    // START-3: 입지 선택
    private String  regionDong;           // 동 선택 (예: "역삼 1동")

    // START-4: 병원 운영 스타일
    private Long    securityDeposit;      // 임대 보증금 (원)
    private Long    interiorCostPerPyung; // 평당 인테리어 금액 (원, 슬라이더)
    private Long    openingMarketing;     // 개원 첫 달 마케팅 비용 (원, 슬라이더)
    private String  clinicStyle;          // 진료 스타일 ("빠른 회전형" | "균형형" | "상담 강화형")
    private String  operationTime;        // 운영 시간 ("기본" | "야간 진료 추가" | "토요일 진료 추가")
    private List<String> equipmentList;   // 의료 기기 목록 (예: ["ECG", "X-Ray", "초음파"])
    private String  staffingStyle;        // 인력 운영 스타일 ("최소 인력 운영" | "균형 운영" | "서비스 강화형")
    private Long    currentAssets;        // 현재 보유 자금 (원, 대출 산정 기준)
}

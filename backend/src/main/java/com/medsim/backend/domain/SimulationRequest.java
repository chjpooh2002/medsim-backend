package com.medsim.backend.domain;

import lombok.Getter;
import lombok.Setter;
import java.util.List;

@Getter
@Setter
public class SimulationRequest {

    private String specialty;       // 진료 과목 (예: "내과")
    private String location;        // 상권 (예: "서초", "강남")
    private int rent;               // 월 임대료 (만원)
    private int selfCapital;        // 초기 자기자본 (만원)
    private int loan;               // 대출금 (만원)
    private double loanRate;        // 대출 연이율 (%)
    private int loanPeriod;         // 대출 상환 기간 (개월)
    private List<Staff> staffList;  // 직원 목록
    private int marketingBudget;    // 월 마케팅 예산 (만원)


    @Getter
    @Setter
    public static class Staff {
        private String role;         // 직종 (예: "간호사")
        private int count;           // 인원수
        private int monthlySalary;   // 월 인건비 (만원)
    }
}

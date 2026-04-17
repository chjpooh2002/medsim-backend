package com.medsim.backend.dto.request;

import lombok.Getter;
import lombok.Setter;

// 인력 구성 입력값 — 직종별 인원수와 월 급여를 받아 인건비 계산에 사용
@Getter
@Setter
public class StaffRequest {

    private String  role;    // 직종 (의사 / 간호사 / 간호조무사 / 원무행정)
    private Integer count;   // 인원수
    private Long    salary;  // 1인당 월 급여 (만원)
}

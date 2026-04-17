package com.medsim.backend.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StaffRequest {

    private String  role;    // 의사 / 간호사 / 간호조무사 / 원무행정
    private Integer count;   // 인원수
    private Long    salary;  // 월 급여 (만원)
}

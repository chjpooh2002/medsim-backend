package com.medsim.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

// KPI 피드백 알림창 1개 — 메시지 텍스트와 심각도 테마를 함께 반환
// theme: "success"(초록) | "warning"(주황) | "danger"(빨강)
@Getter
@AllArgsConstructor
public class KpiMessage {

    private String message;  // 피드백 텍스트
    private String theme;    // 알림창 색상 구분값
}

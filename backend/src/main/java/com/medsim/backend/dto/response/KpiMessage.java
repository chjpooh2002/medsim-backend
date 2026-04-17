package com.medsim.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class KpiMessage {

    private String message;  // 피드백 텍스트
    private String theme;    // "success" | "warning" | "danger"
}

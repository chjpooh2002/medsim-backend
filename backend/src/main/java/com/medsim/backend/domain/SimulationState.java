package com.medsim.backend.domain;

import com.medsim.backend.dto.request.SimulationRequest;
import lombok.*;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SimulationState {

    private String simulationId;
    private int    currentMonth;      // 다음에 실행할 월 (1~36)
    private long   cashBalance;
    private int    patientsPerDay;    // 이번 달 일 평균 환자 수
    private double reputationScore;   // 0~5.0
    private double satisfactionScore; // 0~5.0
    private double returnPatientRate; // 0~1.0
    private double staffMorale;       // 0~1.0
    private List<MonthlyData> monthlyHistory;
    private boolean bankrupt;
    private boolean completed;
    private SimulationRequest initialSetup;

    // 내부용: 향후 발생 예정 이벤트 풀
    private List<SimulationEvent> pendingEvents;
}

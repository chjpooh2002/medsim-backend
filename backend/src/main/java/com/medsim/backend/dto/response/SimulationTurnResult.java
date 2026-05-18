package com.medsim.backend.dto.response;

import com.medsim.backend.domain.Decision;
import com.medsim.backend.domain.MonthlyData;
import com.medsim.backend.domain.SimulationEvent;
import lombok.*;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SimulationTurnResult {

    private String simulationId;
    private int    currentMonth;
    private MonthlyData monthlyData;          // 이번 달 결과
    private List<SimulationEvent> nextEvents; // 다음 달 발생할 이벤트
    private List<Decision> availableDecisions;// 선택 가능한 의사결정 옵션
    private Boolean isBankrupt;
    private Boolean isCompleted;
    private SimulationResult finalResult;     // 완료/파산 시에만 채워짐
}

package com.medsim.backend.controller;

import com.medsim.backend.dto.request.EventResponseRequest;
import com.medsim.backend.dto.request.SimulationRequest;
import com.medsim.backend.dto.response.SimulationResult;
import com.medsim.backend.dto.response.SimulationTurnResult;
import com.medsim.backend.service.SimulationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@Tag(name = "Simulation", description = "개원 시뮬레이션 API")
@RestController
@RequestMapping("/api/simulation")
@RequiredArgsConstructor
public class SimulationController {

    private final SimulationService simulationService;

    @Operation(summary = "시뮬레이션 실행",
            description = "입력값 기반 36개월 손익 · 현금흐름 시뮬레이션")
    @PostMapping
    public ResponseEntity<SimulationResult> simulate(
            @RequestBody SimulationRequest request) {
        return ResponseEntity.ok(simulationService.simulate(request));
    }

    @Operation(summary = "목업 시뮬레이션 결과 조회",
            description = "강남구/내과 기준 36개월 하드코딩 결과 — 프론트 연동용")
    @GetMapping("/mock")
    public ResponseEntity<SimulationResult> mock() {
        return ResponseEntity.ok(simulationService.getMockResult());
    }

    @Operation(summary = "턴제 시뮬레이션 시작",
            description = "1개월차를 실행하고 simulationId 와 상태를 반환. 이후 /{id}/next 로 진행")
    @PostMapping("/start")
    public ResponseEntity<SimulationTurnResult> startSimulation(
            @RequestBody SimulationRequest request) {
        return ResponseEntity.ok(simulationService.startSimulation(request));
    }

    @Operation(summary = "다음 달 진행",
            description = "의사결정 ID 목록을 제출하고 다음 달 시뮬레이션 실행. 완료/파산 시 finalResult 포함")
    @PostMapping("/{id}/next")
    public ResponseEntity<SimulationTurnResult> nextTurn(
            @PathVariable String id,
            @RequestBody List<String> decisionIds) {
        return ResponseEntity.ok(simulationService.nextTurn(id, decisionIds));
    }

    @Operation(summary = "이벤트 대응 옵션 선택",
            description = "발생한 이벤트에 대한 대응 옵션을 선택한다. 다음 턴 진행 시 효과 반영.")
    @PostMapping("/{id}/event-response")
    public ResponseEntity<Map<String, String>> applyEventResponse(
            @PathVariable String id,
            @RequestBody EventResponseRequest request) {
        return ResponseEntity.ok(simulationService.applyEventResponse(id, request.getEventId(), request.getOptionId()));
    }
}

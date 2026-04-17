package com.medsim.backend.controller;

import com.medsim.backend.dto.request.SimulationRequest;
import com.medsim.backend.dto.response.SimulationResult;
import com.medsim.backend.service.SimulationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
}

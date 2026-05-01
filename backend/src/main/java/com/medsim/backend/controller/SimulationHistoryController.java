package com.medsim.backend.controller;

import com.medsim.backend.dto.request.SimulationRequest;
import com.medsim.backend.dto.response.SimulationHistorySummary;
import com.medsim.backend.dto.response.SimulationResult;
import com.medsim.backend.service.SimulationHistoryService;
import com.medsim.backend.service.SimulationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "SimulationHistory", description = "시뮬레이션 이력 API")
@RestController
@RequestMapping("/api/history")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class SimulationHistoryController {

    private final SimulationService simulationService;
    private final SimulationHistoryService historyService;

    @Operation(summary = "시뮬레이션 실행 및 저장",
            description = "시뮬레이션을 실행하고 결과를 로그인 회원의 이력에 저장합니다.")
    @PostMapping
    public ResponseEntity<SimulationResult> runAndSave(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody SimulationRequest request) {
        SimulationResult result = simulationService.simulate(request);
        historyService.save(userDetails.getUsername(), request, result);
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "시뮬레이션 이력 목록",
            description = "로그인한 회원의 시뮬레이션 이력 목록을 최신순으로 조회합니다.")
    @GetMapping
    public ResponseEntity<List<SimulationHistorySummary>> getList(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(historyService.getList(userDetails.getUsername()));
    }

    @Operation(summary = "시뮬레이션 이력 상세",
            description = "특정 시뮬레이션 이력의 전체 결과를 조회합니다.")
    @GetMapping("/{id}")
    public ResponseEntity<SimulationResult> getDetail(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {
        return ResponseEntity.ok(historyService.getDetail(userDetails.getUsername(), id));
    }

    @Operation(summary = "시뮬레이션 이력 삭제",
            description = "특정 시뮬레이션 이력을 삭제합니다.")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {
        historyService.delete(userDetails.getUsername(), id);
        return ResponseEntity.noContent().build();
    }
}

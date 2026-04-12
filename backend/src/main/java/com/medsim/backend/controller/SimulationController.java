package com.medsim.backend.controller;

import com.medsim.backend.domain.SimulationRequest;
import com.medsim.backend.domain.SimulationResult;
import com.medsim.backend.service.SimulationService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/simulation")
public class SimulationController {

    private final SimulationService simulationService;

    // 생성자 주입
    public SimulationController(SimulationService simulationService) {
        this.simulationService = simulationService;
    }

    @PostMapping
    public SimulationResult simulate(@RequestBody SimulationRequest request) {
        return simulationService.simulate(request);
    }
}

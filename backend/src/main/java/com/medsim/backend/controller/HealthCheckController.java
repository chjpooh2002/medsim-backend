package com.medsim.backend.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// 서버 잘 돌아가는지 확인
@RestController
public class HealthCheckController {

    @GetMapping("/health")
    public String health() {
        return "MEDSIM backend is running";
    }
}
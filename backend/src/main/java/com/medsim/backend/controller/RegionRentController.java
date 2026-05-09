package com.medsim.backend.controller;

import com.medsim.backend.dto.response.RegionRentResponse;
import com.medsim.backend.service.RegionRentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/region")
@RequiredArgsConstructor
public class RegionRentController {

    private final RegionRentService regionRentService;

    @GetMapping("/rent")
    public ResponseEntity<List<RegionRentResponse>> getRents(
            @RequestParam(required = false) String district,
            @RequestParam(required = false) String specialty) {
        return ResponseEntity.ok(regionRentService.search(district, specialty));
    }
}

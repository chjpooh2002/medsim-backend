package com.medsim.backend.service;

import com.medsim.backend.dto.response.RegionRentResponse;
import com.medsim.backend.repository.RegionRentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RegionRentService {

    private final RegionRentRepository regionRentRepository;

    public List<RegionRentResponse> search(String district, String specialty) {
        if (district != null && specialty != null) {
            return regionRentRepository.findByDistrictAndSpecialty(district, specialty)
                    .stream().map(RegionRentResponse::new).toList();
        }
        if (district != null) {
            return regionRentRepository.findByDistrict(district)
                    .stream().map(RegionRentResponse::new).toList();
        }
        if (specialty != null) {
            return regionRentRepository.findBySpecialty(specialty)
                    .stream().map(RegionRentResponse::new).toList();
        }
        return regionRentRepository.findAll()
                .stream().map(RegionRentResponse::new).toList();
    }
}

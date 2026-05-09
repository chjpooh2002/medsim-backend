package com.medsim.backend.repository;

import com.medsim.backend.domain.RegionRent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RegionRentRepository extends JpaRepository<RegionRent, Long> {
    List<RegionRent> findByDistrict(String district);
    List<RegionRent> findByDistrictAndSpecialty(String district, String specialty);
    List<RegionRent> findBySpecialty(String specialty);
}

package com.medsim.backend.dto.response;

import com.medsim.backend.domain.RegionRent;
import lombok.Getter;

@Getter
public class RegionRentResponse {

    private final Long id;
    private final String district;
    private final String dong;
    private final String specialty;
    private final Integer avgRentPerPyeong;
    private final Integer minRent;
    private final Integer maxRent;
    private final Integer floor;

    public RegionRentResponse(RegionRent entity) {
        this.id               = entity.getId();
        this.district         = entity.getDistrict();
        this.dong             = entity.getDong();
        this.specialty        = entity.getSpecialty();
        this.avgRentPerPyeong = entity.getAvgRentPerPyeong();
        this.minRent          = entity.getMinRent();
        this.maxRent          = entity.getMaxRent();
        this.floor            = entity.getFloor();
    }
}

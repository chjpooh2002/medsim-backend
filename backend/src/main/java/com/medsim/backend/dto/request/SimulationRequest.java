package com.medsim.backend.dto.request;

import lombok.Getter;
import lombok.Setter;
import java.util.List;

@Getter
@Setter
public class SimulationRequest {

    private String  deptCategory;
    private String  deptDetail;
    private String  regionSiGun;
    private String  regionDetail;
    private Double  lat;
    private Double  lng;
    private Long    initialInvestment;
    private Long    loanAmount;
    private Double  loanRate;
    private Integer loanMonths;
    private Integer rentArea;
    private Long    monthlyRent;
    private Long    monthlyMarketing;
    private List<StaffRequest> staffList;   // inner class → 독립 DTO
}

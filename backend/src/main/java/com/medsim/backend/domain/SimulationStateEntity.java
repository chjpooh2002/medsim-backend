package com.medsim.backend.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "simulation_state")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SimulationStateEntity {

    @Id
    @Column(name = "simulation_id", length = 64)
    private String simulationId;

    @Column(name = "member_email", length = 128)
    private String memberEmail;

    @Lob
    @Column(name = "state_json", columnDefinition = "LONGTEXT")
    private String stateJson;

    @Column(name = "current_month")
    private Integer currentMonth;

    @Column(name = "is_completed")
    private Boolean isCompleted;

    @Column(name = "is_bankrupt")
    private Boolean isBankrupt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}

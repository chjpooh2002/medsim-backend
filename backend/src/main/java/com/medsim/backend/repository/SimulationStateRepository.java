package com.medsim.backend.repository;

import com.medsim.backend.domain.SimulationStateEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SimulationStateRepository extends JpaRepository<SimulationStateEntity, String> {
    Optional<SimulationStateEntity> findBySimulationId(String simulationId);
}

package com.medsim.backend.repository;

import com.medsim.backend.domain.Member;
import com.medsim.backend.domain.SimulationHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SimulationHistoryRepository extends JpaRepository<SimulationHistory, Long> {
    List<SimulationHistory> findByMemberOrderByCreatedAtDesc(Member member);
    Optional<SimulationHistory> findByIdAndMember(Long id, Member member);
}

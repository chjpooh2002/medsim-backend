package com.medsim.backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medsim.backend.domain.Member;
import com.medsim.backend.domain.SimulationHistory;
import com.medsim.backend.dto.request.SimulationRequest;
import com.medsim.backend.dto.response.SimulationHistorySummary;
import com.medsim.backend.dto.response.SimulationResult;
import com.medsim.backend.repository.MemberRepository;
import com.medsim.backend.repository.SimulationHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SimulationHistoryService {

    private final SimulationHistoryRepository historyRepository;
    private final MemberRepository memberRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void save(String email, SimulationRequest request, SimulationResult result) {
        Member member = findMember(email);

        historyRepository.save(SimulationHistory.builder()
                .member(member)
                .deptCategory(request.getDeptCategory())
                .regionSiGun(request.getRegionSiGun())
                .bepMonth(result.getBepMonth())
                .fixedCostRatio(result.getFixedCostRatio())
                .finalCashBalance(result.getFinalCashBalance())
                .requestJson(toJson(request))
                .resultJson(toJson(result))
                .build());
    }

    @Transactional(readOnly = true)
    public List<SimulationHistorySummary> getList(String email) {
        Member member = findMember(email);

        return historyRepository.findByMemberOrderByCreatedAtDesc(member).stream()
                .map(h -> SimulationHistorySummary.builder()
                        .id(h.getId())
                        .deptCategory(h.getDeptCategory())
                        .regionSiGun(h.getRegionSiGun())
                        .bepMonth(h.getBepMonth())
                        .fixedCostRatio(h.getFixedCostRatio())
                        .finalCashBalance(h.getFinalCashBalance())
                        .createdAt(h.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public SimulationResult getDetail(String email, Long historyId) {
        Member member = findMember(email);

        SimulationHistory history = historyRepository.findByIdAndMember(historyId, member)
                .orElseThrow(() -> new IllegalArgumentException("시뮬레이션 이력을 찾을 수 없습니다."));

        try {
            return objectMapper.readValue(history.getResultJson(), SimulationResult.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("결과 데이터 파싱 실패", e);
        }
    }

    @Transactional
    public void delete(String email, Long historyId) {
        Member member = findMember(email);

        SimulationHistory history = historyRepository.findByIdAndMember(historyId, member)
                .orElseThrow(() -> new IllegalArgumentException("시뮬레이션 이력을 찾을 수 없습니다."));

        historyRepository.delete(history);
    }

    private Member findMember(String email) {
        return memberRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("회원을 찾을 수 없습니다."));
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON 직렬화 실패", e);
        }
    }
}

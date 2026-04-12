package com.medsim.backend.service;

import com.medsim.backend.domain.MonthlyData;
import com.medsim.backend.domain.SimulationRequest;
import com.medsim.backend.domain.SimulationResult;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

@Service
public class SimulationService {

    // 서초/강남 내과 기준 고정값 (나중에 DB로 교체할 부분)
    private static final int BASE_PATIENTS = 40;    // 일 평균 환자수
    private static final int WORKING_DAYS     = 22;     // 월 진료일수
    private static final int SALARY_PRICE     = 42011;  // 급여 객단가
    private static final double NON_SALARY_RATIO  = 0.15;
    private static final int NON_SALARY_PRICE = 50000;

    public SimulationResult simulate(SimulationRequest req) {
        List<MonthlyData> monthlyList = new ArrayList<>(); // 36개월 데이터를 받을 빈 리스트

        // 초기 자본금을 원 단위로 설정
        long initialCapital = req.getTotalInvestment();  // 총 투자금
        long cashflow = initialCapital;                  // 현금잔고 시작값 = 투자금

        int bepMonth = -1;                               // BEP 아직 못 찾음 (-1 = 미달성)
        long totalProfit = 0;                            // 누적 순이익

        // minCashflow의 초기값을 시작 자본금으로 설정
        long minCashflow = initialCapital;               // 최저 현금 초기값

        for (int month = 1; month <= 36; month++) {
            double growth = Math.min(1.0, 0.3 + month * 0.03);  //환자 유입률을 의미. 최대가 100%(= 1.0)
            long salaryRevenue    = (long)(BASE_PATIENTS * WORKING_DAYS * growth * SALARY_PRICE);  // 급여 매출 = 일 환자수(40) × 진료일수(22) × 성장률 × 급여 객단가(42,011원)
            long nonSalaryRevenue = (long)(BASE_PATIENTS * WORKING_DAYS * growth * NON_SALARY_RATIO * NON_SALARY_PRICE); // 비급여 매출
            long revenue          = salaryRevenue + nonSalaryRevenue;
            long fixedCost = calcFixedCost(req);   // 고정비 계산 (아래 메서드 호출)
            long profit = revenue - fixedCost;     // 순이익 = 매출 - 고정비

            cashflow += profit;  // 현금 잔고 누적
            totalProfit += profit;   // 순이익 누적

            // BEP: 월별 수익(profit)이 처음으로 플러스가 되는 달
            if (bepMonth == -1 && profit > 0) {
                bepMonth = month;
            }

            // 최저 현금 잔고 갱신 (자본금 포함 실제 잔고 기준)
            if (cashflow < minCashflow) {
                minCashflow = cashflow;
            }

            monthlyList.add(MonthlyData.builder() // 매달 계산 결과를 MonthlyData 객체로 만들어서 리스트에 추가
                    .month(month)
                    .revenue(revenue)
                    .fixedCost(fixedCost)
                    .profit(profit)
                    .cashflow(cashflow)
                    .build());
        }

        return SimulationResult.builder()
                .bepMonth(bepMonth)
                .totalProfit36(totalProfit)
                .minCashflow(minCashflow) // 이제 자본금 포함 최저 잔고가 나옵니다
                .monthly(monthlyList)
                .kpiMessages(generateKpiMessages(bepMonth, minCashflow, initialCapital)) // 파라미터 수정
                .build();
    }

    private static final Map<String, Long> SALARY_MAP;

    static {
        SALARY_MAP = new HashMap<>();
        SALARY_MAP.put("의사",      8_000_000L);
        SALARY_MAP.put("간호사",    3_950_000L);
        SALARY_MAP.put("간호조무사", 2_330_000L);
        SALARY_MAP.put("원무/행정", 2_330_000L);
    }

    // 마케팅 비용 계산 (DB 시트 단가 기준)
    private static final Map<String, Long> MARKETING_PRICE;

    static {
        MARKETING_PRICE = new HashMap<>();
        MARKETING_PRICE.put("병원 홈페이지 제작", 3_000_000L);
        MARKETING_PRICE.put("블로그 운영",       300_000L);
        MARKETING_PRICE.put("SNS 계정 운영",    200_000L);
        MARKETING_PRICE.put("블로그 체험단",     150_000L);
        MARKETING_PRICE.put("인플루언서 협찬",   500_000L);
        MARKETING_PRICE.put("네이버 GDN 배너",  500_000L);
        MARKETING_PRICE.put("현수막/간판",      200_000L);
        MARKETING_PRICE.put("전단지 배포",      300_000L);
        MARKETING_PRICE.put("개원 이벤트",    1_000_000L);
        MARKETING_PRICE.put("네이버 플레이스",  100_000L);
        MARKETING_PRICE.put("네이버 키워드광고", 500_000L);
        MARKETING_PRICE.put("구글 검색광고",    300_000L);
    }

    private long calcFixedCost(SimulationRequest req) {

        // 인건비 (직종별 평균급여 × 인원수)
        long staffCost = req.getStaffList().stream()
                .mapToLong(s -> SALARY_MAP.getOrDefault(s.getRole(), 0L) * s.getCount())
                .sum();

        // 4대보험료 (인건비 × 10.6%)
        long insurance = (long)(staffCost * 0.106);

        // 수정 (서초강남 B급 현실적 수치)
        long rent = (long) req.getArea() * 120_000L; // 평당 12만원

        // 감가상각비 (총투자금 ÷ 60개월)
        long depreciation = req.getTotalInvestment() / 60;

        // 마케팅비 계산
        long marketingCost = calcMarketingCost(req.getMarketingList());

        // 기타관리비는 매출의 2% → Service에서 처리

        return staffCost + insurance + rent + depreciation + marketingCost;
    }

    private long calcMarketingCost(List<SimulationRequest.Marketing> list) {
        if (list == null) return 0L;

        return list.stream()
                .filter(SimulationRequest.Marketing::isSelected) // 선택된 항목만
                .mapToLong(m -> {
                    long price = MARKETING_PRICE.getOrDefault(m.getItem(), 0L);
                    // 1회성은 첫 달만 반영 → 여기선 월 고정비로 단순화
                    // "월"과 "건당"은 quantity 곱함
                    if ("1회성".equals(m.getType())) return 0L; // 별도 처리 필요
                    return price * Math.max(1, m.getQuantity());
                })
                .sum();
    }


    // KPI 진단 메시지 생성
    private List<String> generateKpiMessages(int bepMonth, long minCashflow, long initialCapital) {
        List<String> messages = new ArrayList<>();

        // 실제 잔고가 0 밑으로 내려가면 진짜 위험한 상황!
        if (minCashflow < 0) {
            messages.add("운전자본 고갈 위험 — 현재 구성으로는 초기 자본금이 부족합니다.");
        } else if (minCashflow < initialCapital * 0.2) {
            messages.add("현금 흐름 주의 — 자본금의 20% 미만으로 잔고가 떨어지는 시점이 있습니다.");
        }

        if (bepMonth == -1) {
            messages.add("36개월 내 월 단위 흑자 전환 미달성 — 비용 구조 재검토 필요");
        } else {
            messages.add("BEP " + bepMonth + "개월 달성 — " + (bepMonth <= 12 ? "매우 양호" : "평균 수준"));
        }

        return messages;
    }
}
